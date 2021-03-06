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

package org.headsupdev.agile.storage.issues;

import org.headsupdev.support.java.StringUtil;

import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * An embeddable model of duration comprising a time value and a unit.
 *
 * @author Andrew Williams
 * @version $Id$
 * @since 1.0
 */
@Embeddable
public class Duration
        implements Serializable
{
    public static final String UNIT_HOURS = "hours";
    public static final String UNIT_DAYS = "days";
    public static final String UNIT_WEEKS = "weeks";
    public static final String UNIT_MINUTES = "minutes";

    public static final List<Integer> OPTIONS_MINUTE = Arrays.asList( 0, 15, 30, 45 );
    public static final List<String> OPTIONS_TIME_UNIT = Arrays.asList( Duration.UNIT_HOURS, Duration.UNIT_DAYS, Duration.UNIT_WEEKS );

    public static final int HOUR_UNITS_IN_DAY = 8;
    public static final int DAY_UNITS_IN_WEEK = 5;
    public static final int MINUTE_UNITS_IN_HOUR = 60;

    private Integer time;
    private String timeUnit = UNIT_HOURS;

    public static Duration combine( Duration... durations )
    {
        if ( durations == null )
        {
            return null;
        }

        if ( durations.length == 1 )
        {
            return durations[ 0 ];
        }

        int minutes = 0;
        for ( Duration duration : durations )
        {
            minutes += duration.getTimeAsMinutes();
        }

        return new Duration( ( (double) minutes ) / MINUTE_UNITS_IN_HOUR );
    }

    public Duration()
    {
    }

    /**
     * copy constructor
     *
     * @param toCopy
     */
    public Duration( Duration toCopy )
    {
        this.timeUnit = toCopy.timeUnit;
        this.time = toCopy.time;
    }

    public Duration( int time, String unit )
    {
        this.time = time;
        this.timeUnit = unit;
    }

    public Duration( double hours )
    {
        setFieldsBasedOnHours( hours );
    }

    private void setFieldsBasedOnHours( double hours )
    {
        this.timeUnit = Duration.getMultiplierName( hours );
        this.time = (int) ( hours / Duration.getMultiplier( this.timeUnit ) );
    }

    public Integer getTime()
    {
        return ( time == null ) ? 0 : time;
    }

    public void setTime( Integer time )
    {
        this.time = time;
    }

    public String getTimeUnit()
    {
        if ( timeUnit == null )
        {
            return UNIT_HOURS;
        }

        return timeUnit;
    }

    public void setTimeUnit( String timeUnit )
    {
        this.timeUnit = timeUnit;
    }

    public double getHours()
    {
        if ( time == null || time == 0 )
        {
            return 0;
        }

        return time * getMultiplier( getTimeUnit() );
    }

    /**
     * returns the number of hours contained within this duration without reporting any remainder -- ie the minutes
     * eg, if this duration object was 2hrs 45 minutes, this would return 2.
     *
     * @return
     */
    public int getWholeHours()
    {
        return (int) Math.floor( getHours() );
    }

    /**
     * returns the number of whole minutes
     * Ie, if this duration was 2hrs 45 minutes, this would return 45.
     *
     * @return
     */
    public int getWholeMinutes()
    {
        // internally if we are storing any fractions of an hour, then the timeUnit will be in minutes.
        // therefore it's safe to assume we only report a value if the timeUnit is MINUTES.
        if ( timeUnit.equalsIgnoreCase( UNIT_MINUTES ) )
        {
            return time % MINUTE_UNITS_IN_HOUR;
        }
        return 0;
    }

    /**
     * Safest value to return from this object as it will avoid any rounding issues.
     * that is until we start logging seconds? ;-)
     *
     * @return
     */
    public int getTimeAsMinutes()
    {
        if ( timeUnit.equalsIgnoreCase( UNIT_MINUTES ) )
        {
            return time;
        }

        return (int) ( getHours() * MINUTE_UNITS_IN_HOUR );
    }

    /**
     * we need a way to allow hours to be set using doubles, therefore the time setter is not
     * suitable due to taking an int. This will alter the timeUnit field if required depending on the value passed in
     *
     * @param hours
     */
    public void setHours( double hours )
    {
        setFieldsBasedOnHours( hours );
    }

    public String toString()
    {
        if ( time == null || time == 0 )
        {
            return "0h";
        }

        StringBuilder ret = new StringBuilder();
        int minutes = getWholeMinutes();
        int hours = getWholeHours() % HOUR_UNITS_IN_DAY;
        int days = ( ( getWholeHours() - hours ) / HOUR_UNITS_IN_DAY ) % DAY_UNITS_IN_WEEK;
        int weeks = ( ( ( getWholeHours() - hours ) / HOUR_UNITS_IN_DAY ) ) / DAY_UNITS_IN_WEEK;

        if ( weeks > 0 )
        {
            ret.append( weeks );
            ret.append( "w " );
        }
        if ( days > 0 )
        {
            ret.append( days );
            ret.append( "d " );
        }
        if ( hours > 0 )
        {
            ret.append( hours );
            ret.append( "h " );
        }
        if ( minutes > 0 )
        {
            ret.append( minutes );
            ret.append( "m" );
        }

        return ret.toString().trim();
    }

    public String toHoursString()
    {
        if ( time == null || time == 0 )
        {
            return "0h";
        }

        StringBuilder ret = new StringBuilder();
        int minutes = getWholeMinutes();
        int hours = getWholeHours();

        if ( hours > 0 )
        {
            ret.append( hours );
            ret.append( "h " );
        }
        if ( minutes > 0 )
        {
            ret.append( minutes );
            ret.append( "m" );
        }

        return ret.toString().trim();
    }

    public static double getMultiplier( String name )
    {
        if ( StringUtil.isEmpty( name ) )
        {
            return 1;
        }

        if ( name.equalsIgnoreCase( Duration.UNIT_WEEKS ) )
        {
            return Duration.HOUR_UNITS_IN_DAY * Duration.DAY_UNITS_IN_WEEK;
        }
        if ( name.equalsIgnoreCase( Duration.UNIT_DAYS ) )
        {
            return Duration.HOUR_UNITS_IN_DAY;
        }
        if ( name.equalsIgnoreCase( UNIT_MINUTES ) )
        {
            return 1d / MINUTE_UNITS_IN_HOUR;
        }

        return 1;
    }

    public static String getMultiplierName( double hours )
    {
        boolean containsFractionsOfHours = ( hours - Math.floor( hours ) ) != 0d;

        if ( containsFractionsOfHours )
        {
            return Duration.UNIT_MINUTES;
        }
        if ( hours >= Duration.HOUR_UNITS_IN_DAY * Duration.DAY_UNITS_IN_WEEK &&
                hours % ( Duration.HOUR_UNITS_IN_DAY * Duration.DAY_UNITS_IN_WEEK ) == 0 )
        {
            return Duration.UNIT_WEEKS;
        }

        if ( hours >= Duration.HOUR_UNITS_IN_DAY && hours % Duration.HOUR_UNITS_IN_DAY == 0 )
        {
            return Duration.UNIT_DAYS;
        }

        return Duration.UNIT_HOURS;
    }
}
