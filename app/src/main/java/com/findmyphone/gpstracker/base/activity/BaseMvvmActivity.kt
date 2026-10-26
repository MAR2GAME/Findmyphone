package com.findmyphone.gpstracker.base.activity

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.findmyphone.gpstracker.ad.AdvActivity
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import java.lang.reflect.ParameterizedType

abstract class BaseMvvmActivity<V : ViewBinding, VM : BaseViewModel> : AdvActivity() {

    protected lateinit var binding: V
        private set

    protected lateinit var viewModel: VM
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initializeBinding()
        initializeViewModel()
        initializeActivity()
    }

    private fun initializeBinding() {
        try {
            binding = createBinding()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to initialize ViewBinding for ${this::class.java.simpleName}. " +
                        "Ensure the first generic parameter is a concrete ViewBinding class.",
                e
            )
        }
        setContentView(binding.root)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createBinding(): V {
        val bindingClass = resolveGenericClass(0)
        val inflateMethod = bindingClass.getDeclaredMethod("inflate", LayoutInflater::class.java)
        return inflateMethod.invoke(null, layoutInflater) as V
    }

    private fun initializeViewModel() {
        try {
            viewModel = createViewModel()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to initialize ViewModel for ${this::class.java.simpleName}. " +
                        "ViewModel class: ${resolveGenericClass(1).simpleName}",
                e
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun createViewModel(): VM {
        val viewModelClass = resolveGenericClass(1) as Class<VM>
        return ViewModelProvider(this).get(viewModelClass)
    }


    @CallSuper
    protected open fun initializeActivity() {
        init()
    }

    protected abstract fun init()

    /**
     * 解析指定索引的泛型参数类，支持多层继承链。
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveGenericClass(index: Int): Class<*> {
        var currentClass: Class<*> = this.javaClass
        while (currentClass != BaseMvvmActivity::class.java) {
            val superclass = currentClass.genericSuperclass
            if (superclass is ParameterizedType) {
                val typeArgs = superclass.actualTypeArguments
                if (typeArgs.size > index) {
                    val typeArg = typeArgs[index]
                    if (typeArg is Class<*>) {
                        return typeArg
                    }
                    // 如果仍然是类型变量（TypeVariable），继续向上查找
                }
            }
            currentClass = currentClass.superclass
        }
        throw IllegalStateException(
            "Cannot resolve generic type at index $index for ${this::class.java.simpleName}. " +
                    "Ensure the class hierarchy directly specifies concrete types."
        )
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}