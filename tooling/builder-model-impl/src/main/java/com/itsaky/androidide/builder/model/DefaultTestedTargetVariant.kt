/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.TestedTargetVariant
import java.io.Serializable

/** @author Akash Yadav */
class DefaultTestedTargetVariant : TestedTargetVariant, Serializable {
    companion object {
        private const val serialVersionUID = 1L
        
        /**
         * Create a DefaultTestedTargetVariant instance from a TestedTargetVariant model.
         *
         * @param targetVariant The TestedTargetVariant model from AGP
         * @return A DefaultTestedTargetVariant instance
         */
        @JvmStatic
        fun fromTestedTargetVariant(targetVariant: TestedTargetVariant): DefaultTestedTargetVariant {
            return DefaultTestedTargetVariant().apply {
                this.targetProjectPath = targetVariant.targetProjectPath
                this.targetVariant = targetVariant.targetVariant
            }
        }
    }
    
    override var targetProjectPath: String = ""
    override var targetVariant: String = ""
}
