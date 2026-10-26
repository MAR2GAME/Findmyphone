import android.app.Application
import androidx.camera.core.impl.utils.ContextUtil.getApplication
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.CheckUserData
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpException
import kotlinx.coroutines.launch
import java.io.IOException

class ScannerViewModel(application: Application) : BaseViewModel(application) {

    val isLoading = MutableLiveData<Boolean>()
    val isCheckState = MutableLiveData<Boolean>()
    val errorMessage = MutableLiveData<String>()   // 用于向Activity传递错误文本

    fun searchFriend(code: String) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val map = mapOf("securityCode" to code)
                val checkUserData = HttpClient.getForData<CheckUserData>(
                    AppConfig.BASE_URL + "/api/v1/users/exists-by-security-code",
                    map
                )
                if (checkUserData != null && checkUserData.exists) {
                    isCheckState.value = true
                } else {
                    isCheckState.value = false
                    errorMessage.value = getApplication<Application>().getString(R.string.scanning_error)
                }
            } catch (e: HttpException) {
                isCheckState.value = false
                errorMessage.value = getApplication<Application>().getString(R.string.request_error)
            } catch (e: IOException) {
                isCheckState.value = false
                errorMessage.value = getApplication<Application>().getString(R.string.check_your_network)
            } catch (e: Exception) {
                isCheckState.value = false
                errorMessage.value = getApplication<Application>().getString(R.string.request_error)
            }
        }
    }
}