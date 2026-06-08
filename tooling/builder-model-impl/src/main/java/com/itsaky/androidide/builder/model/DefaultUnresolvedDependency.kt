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

import com.android.builder.model.v2.ide.UnresolvedDependency
import java.io.Serializable

/** @author Akash Yadav */
data class DefaultUnresolvedDependency(override val cause: String?, override val name: String) :
    UnresolvedDependency, Serializable {
    companion object {
        private const val serialVersionUID = 1L
        
        /**
         * Create a DefaultUnresolvedDependency instance from an UnresolvedDependency model.
         *
         * @param unresolvedDependency The UnresolvedDependency model from AGP
         * @return A DefaultUnresolvedDependency instance
         */
        @JvmStatic
        fun fromUnresolvedDependency(unresolvedDependency: UnresolvedDependency): DefaultUnresolvedDependency {
            return DefaultUnresolvedDependency(
                cause = unresolvedDependency.cause,
                name = unresolvedDependency.name
            )
        }
    }
    
    private val serialVersionUID = 1L
}
