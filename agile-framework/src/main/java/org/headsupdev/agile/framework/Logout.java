/*
 * HeadsUp Agile
 * Copyright 2009-2012 Heads Up Development Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.headsupdev.agile.framework;

import org.headsupdev.agile.web.dialogs.LogoutDialog;

import org.headsupdev.agile.api.Permission;
import org.headsupdev.agile.web.MountPoint;
import org.headsupdev.agile.web.HeadsUpPage;

/**
 * The HeadsUp logout page.
 *
 * @author Andrew Williams
 * @version $Id$
 * @since 1.0
 */
@MountPoint( "logout" )
public class Logout
    extends HeadsUpPage
{
    public Permission getRequiredPermission()
    {
        return null;
    }

    public void layout()
    {
        super.layout();

        add( new LogoutDialog( "dialog", false, this ) );
    }
}
