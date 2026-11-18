import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

private const val selectionState =
    "androidx.compose.foundation.text.input.internal.selection.TextFieldSelectionState"
private const val layoutCache =
    "androidx.compose.foundation.text.input.internal.TextFieldLayoutStateCache"
private const val textCanvas = "androidx.compose.ui.text.android.TextAndroidCanvas"
private const val helper = "yancey/chelper/android/performance/ComposeTextFieldPerformance"
private const val plainTextDraw = "(Ljava/lang/String;FFLandroid/graphics/Paint;)V"

abstract class ComposeTextFieldPerformanceVisitor :
    AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun isInstrumentable(classData: ClassData): Boolean =
        when (classData.className) {
            selectionState,
            layoutCache,
            textCanvas -> true
            else -> false
        }

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor {
        val ownerClass = classContext.currentClassData.className
        val methodName =
            when (ownerClass) {
                selectionState -> "calculateCursorRect"
                layoutCache -> "getOrComputeLayout"
                else -> "drawText"
            }
        return object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
            private var redirected = 0

            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor {
                val method = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name != methodName || (ownerClass == textCanvas && descriptor != plainTextDraw))
                    return method
                return object : MethodVisitor(Opcodes.ASM9, method) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        name: String,
                        descriptor: String,
                        isInterface: Boolean,
                    ) {
                        val cursor =
                            ownerClass == selectionState &&
                                opcode == Opcodes.INVOKEVIRTUAL &&
                                owner == "androidx/compose/ui/text/TextLayoutResult" &&
                                name == "getCursorRect" &&
                                descriptor == "(I)Landroidx/compose/ui/geometry/Rect;"
                        val sameText =
                            ownerClass == layoutCache &&
                                opcode == Opcodes.INVOKESTATIC &&
                                owner == "kotlin/text/StringsKt" &&
                                name == "contentEquals" &&
                                descriptor == "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z"
                        val drawText =
                            ownerClass == textCanvas &&
                                opcode == Opcodes.INVOKEVIRTUAL &&
                                owner == "android/graphics/Canvas" &&
                                name == "drawText" &&
                                descriptor == plainTextDraw
                        if (cursor || sameText || drawText) {
                            redirected++
                            val replacement =
                                when {
                                    cursor -> "cursorRect"
                                    sameText -> "sameText"
                                    else -> "drawPlainText"
                                }
                            val replacementDescriptor =
                                when {
                                    cursor ->
                                        "(Landroidx/compose/ui/text/TextLayoutResult;I)Landroidx/compose/ui/geometry/Rect;"
                                    drawText ->
                                        "(Landroid/graphics/Canvas;Ljava/lang/String;FFLandroid/graphics/Paint;)V"
                                    else -> descriptor
                                }
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                helper,
                                replacement,
                                replacementDescriptor,
                                false,
                            )
                        } else {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
            }

            override fun visitEnd() {
                check(redirected == 1) {
                    "$ownerClass.$methodName changed. Revalidate the text field optimization."
                }
                super.visitEnd()
            }
        }
    }
}
