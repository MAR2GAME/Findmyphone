package com.findmyphone.gpstracker.base.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import java.lang.reflect.ParameterizedType

abstract class BaseMvvmFragment<V : ViewBinding, VM : BaseViewModel> : Fragment() {

    private var _binding: V? = null
    protected val binding: V
        get() = _binding ?: throw IllegalStateException("ViewBinding is null. Ensure onViewCreated has been called.")

    protected lateinit var viewModel: VM
        private set

    @LayoutRes
    protected open fun getLayoutResId(): Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        initializeBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViewModel()
        init()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 初始化ViewBinding
     */
    private fun initializeBinding(inflater: LayoutInflater, container: ViewGroup?) {
        try {
            val bindingClass = getGenericBindingClass()
            val inflateMethod = bindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.java)
            @Suppress("UNCHECKED_CAST")
            _binding = inflateMethod.invoke(null, inflater, container, false) as V
        } catch (e: Exception) {
            handleBindingInitializationError(e, inflater)
        }
    }

    private fun handleBindingInitializationError(error: Exception, inflater: LayoutInflater) {
        val layoutResId = getLayoutResId()
        if (layoutResId != 0) {
            // 回退到传统布局设置方式，但由于ViewBinding无法创建，这里抛出异常要求子类正确使用ViewBinding
            throw IllegalStateException(
                "ViewBinding initialization failed. Ensure your layout has ViewBinding enabled.",
                error
            )
        } else {
            throw IllegalStateException(
                "ViewBinding initialization failed. Ensure your layout has ViewBinding enabled or override getLayoutResId() to provide fallback layout.",
                error
            )
        }
    }

    /**
     * 获取ViewBinding的泛型类
     */
    @Suppress("UNCHECKED_CAST")
    private fun getGenericBindingClass(): Class<V> {
        return try {
            val superclass = javaClass.genericSuperclass as? ParameterizedType
                ?: throw IllegalArgumentException("Missing generic type parameters")

            superclass.actualTypeArguments[0] as Class<V>
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to get ViewBinding generic type", e)
        }
    }

    /**
     * 获取ViewModel的泛型类
     */
    @Suppress("UNCHECKED_CAST")
    private fun getGenericViewModelClass(): Class<VM> {
        return try {
            val superclass = javaClass.genericSuperclass as? ParameterizedType
                ?: throw IllegalArgumentException("Missing generic type parameters")

            superclass.actualTypeArguments[1] as Class<VM>
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to get ViewModel generic type", e)
        }
    }

    /**
     * 创建ViewModel实例
     */
    protected open fun createViewModel(): VM {
        return ViewModelProvider(this)[getGenericViewModelClass()]
    }

    /**
     * 初始化ViewModel
     */
    private fun initializeViewModel() {
        try {
            viewModel = createViewModel()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialize ViewModel for ${getGenericViewModelClass().simpleName}", e)
        }
    }

    /**
     * 抽象方法，子类实现初始化逻辑
     */
    protected abstract fun init()
}