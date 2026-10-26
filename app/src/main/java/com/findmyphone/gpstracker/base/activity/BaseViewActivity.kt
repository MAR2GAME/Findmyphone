package com.findmyphone.gpstracker.base.activity

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.findmyphone.gpstracker.ad.AdvActivity
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable

abstract class BaseViewActivity<V : ViewBinding> : AdvActivity()  {
    protected lateinit var binding: V
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initBinding()
        initializeActivity()
    }

    @Suppress("UNCHECKED_CAST")
    private fun initBinding() {
        binding = try {
            val bindingClass = resolveBindingClass()
            val inflateMethod = bindingClass.getDeclaredMethod("inflate", LayoutInflater::class.java)
            inflateMethod.invoke(null, layoutInflater) as V
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to initialize ViewBinding for ${this::class.java.simpleName}. " +
                        "Make sure the first generic parameter is a concrete ViewBinding class.",
                e
            )
        }
        setContentView(binding.root)
    }

    /**
     * 递归解析 ViewBinding 的泛型类型，支持中间泛型类链。
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveBindingClass(): Class<V> {
        var currentClass: Class<*> = this.javaClass
        while (currentClass != BaseViewActivity::class.java) {
            val superclass = currentClass.genericSuperclass
            if (superclass is ParameterizedType) {
                val typeArg = superclass.actualTypeArguments[0]
                if (typeArg is Class<*>) {
                    return typeArg as Class<V>
                }
            }
            currentClass = currentClass.superclass
        }
        throw IllegalStateException("Cannot resolve ViewBinding type. Are generics properly defined?")
    }

    @CallSuper
    protected open fun initializeActivity() {
        // 子类可重写进行初始化
    }



    protected open fun cleanup() {
        // 子类可重写清理资源
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}