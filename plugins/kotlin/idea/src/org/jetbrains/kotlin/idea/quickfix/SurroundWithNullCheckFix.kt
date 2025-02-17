// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isStableSimpleExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getLastParentOfTypeInRow
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.utils.findVariable
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isNullabilityMismatch
import org.jetbrains.kotlin.util.match

class SurroundWithNullCheckFix(
    expression: KtExpression,
    nullableExpression: KtExpression
) : KotlinQuickFixAction<KtExpression>(expression), HighPriorityAction {
    private val nullableExpressionPointer = nullableExpression.createSmartPointer()

    override fun getFamilyName() = text

    override fun getText() = KotlinBundle.message("surround.with.null.check")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val nullableExpression = nullableExpressionPointer.element ?: return
        val psiFactory = KtPsiFactory(project)
        val surrounded = psiFactory.createExpressionByPattern("if ($0 != null) { $1 }", nullableExpression, element)
        element.replace(surrounded)
    }

    companion object : KotlinSingleIntentionActionFactory() {

        private fun KtExpression.hasAcceptableParent() = with(parent) {
            this is KtBlockExpression || this.parent is KtIfExpression ||
                    this is KtWhenEntry || this.parent is KtLoopExpression
        }

        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = diagnostic.psiElement
            val expressionParent = element.getParentOfType<KtExpression>(strict = element is KtOperationReferenceExpression) ?: return null
            val context = expressionParent.analyze(BodyResolveMode.PARTIAL_WITH_CFA)

            val parent = element.parent
            val nullableExpression =
                when (parent) {
                    is KtDotQualifiedExpression -> parent.receiverExpression
                    is KtBinaryExpression -> if (parent.operationToken == KtTokens.IN_KEYWORD) parent.right else parent.left
                    is KtCallExpression -> parent.calleeExpression
                    else -> return null
                } as? KtReferenceExpression ?: return null

            if (!nullableExpression.isStableSimpleExpression(context)) return null

            val expressionTarget = expressionParent.getParentOfTypesAndPredicate(
                strict = false, parentClasses = arrayOf(KtExpression::class.java)
            ) {
                !it.isUsedAsExpression(context) && it.hasAcceptableParent()
            } ?: return null
            // Surround declaration (even of local variable) with null check is generally a bad idea
            if (expressionTarget is KtDeclaration) return null

            val declaration = nullableExpression.mainReference.resolveToDescriptors(context).singleOrNull() ?: return null
            val variable =
                expressionTarget.getResolutionScope(context)?.findVariable(declaration.name, NoLookupLocation.FROM_IDE) ?: return null
            if (declaration != variable) return null

            return SurroundWithNullCheckFix(expressionTarget, nullableExpression)
        }
    }

    object IteratorOnNullableFactory : KotlinSingleIntentionActionFactory() {

        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val nullableExpression = diagnostic.psiElement as? KtReferenceExpression ?: return null
            val forExpression = nullableExpression.parents.match(KtContainerNode::class, last = KtForExpression::class) ?: return null
            if (forExpression.parent !is KtBlockExpression) return null

            if (!nullableExpression.isStableSimpleExpression()) return null

            return SurroundWithNullCheckFix(forExpression, nullableExpression)
        }
    }

    object TypeMismatchFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val nullableExpression = diagnostic.psiElement as? KtReferenceExpression ?: return null
            val expectedType: KotlinType
            val actualType: KotlinType
            when (diagnostic.factory) {
                Errors.TYPE_MISMATCH -> {
                    val diagnosticWithParameters = Errors.TYPE_MISMATCH.cast(diagnostic)
                    expectedType = diagnosticWithParameters.a
                    actualType = diagnosticWithParameters.b
                }

                Errors.TYPE_MISMATCH_WARNING -> {
                    val diagnosticWithParameters = Errors.TYPE_MISMATCH_WARNING.cast(diagnostic)
                    expectedType = diagnosticWithParameters.a
                    actualType = diagnosticWithParameters.b
                }

                ErrorsJvm.NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS -> {
                    val diagnosticWithParameters = ErrorsJvm.NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.cast(diagnostic)
                    expectedType = diagnosticWithParameters.a
                    actualType = diagnosticWithParameters.b
                }

                else -> return null
            }
            val root = when (val parent = nullableExpression.parent) {
                is KtValueArgument -> {
                    val call = parent.getParentOfType<KtCallExpression>(true) ?: return null
                    call.getLastParentOfTypeInRow<KtQualifiedExpression>() ?: call
                }

                is KtBinaryExpression -> {
                    if (parent.right != nullableExpression) return null
                    parent
                }

                else -> return null
            }
            if (root.parent !is KtBlockExpression) return null
            if (!isNullabilityMismatch(expected = expectedType, actual = actualType)) return null
            if (!nullableExpression.isStableSimpleExpression()) return null
            return SurroundWithNullCheckFix(root, nullableExpression)
        }
    }
}
