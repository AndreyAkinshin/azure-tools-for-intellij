/**
 * Copyright (c) 2018 JetBrains s.r.o.
 * <p/>
 * All rights reserved.
 * <p/>
 * MIT License
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.microsoft.intellij.runner

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import javax.swing.JComponent

abstract class AzureRiderSettingsEditor<T : AzureRunConfigurationBase<*>> : SettingsEditor<T>() {

    protected abstract val panel: AzureRiderSettingPanel<T>

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: T) {
        panel.apply(configuration)
        configuration.checkConfiguration()
    }

    override fun resetEditorFrom(configuration: T) {
        configuration.isFirstTimeCreated = false
        panel.reset(configuration)
    }

    override fun createEditor(): JComponent {
        return panel.mainPanel
    }

    override fun disposeEditor() {
        panel.disposeEditor()
        super.disposeEditor()
    }
}
