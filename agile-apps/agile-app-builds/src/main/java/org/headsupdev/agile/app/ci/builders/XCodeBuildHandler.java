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

package org.headsupdev.agile.app.ci.builders;

import org.headsupdev.agile.storage.HibernateStorage;
import org.headsupdev.agile.storage.ci.TestResult;
import org.headsupdev.agile.storage.ci.TestResultSet;
import org.headsupdev.support.java.IOUtil;
import org.headsupdev.agile.api.*;
import org.headsupdev.agile.api.logging.Logger;
import org.headsupdev.agile.app.ci.CIApplication;
import org.headsupdev.agile.storage.ci.Build;
import org.headsupdev.support.java.StringUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;

/**
 * The main code for building an xcode project.
 *
 * @author Andrew Williams
 * @since 1.0
 */
public class XCodeBuildHandler
{
    private static Logger log = Manager.getLogger( XCodeBuildHandler.class.getName() );

    protected static void runBuild( XCodeProject project, PropertyTree config, File dir, File output,
                                               Build build, long buildId )
    {
        String confName = config.getProperty( CIApplication.CONFIGURATION_XCODE_CONFIG.getKey(),
                (String) CIApplication.CONFIGURATION_XCODE_CONFIG.getDefault() );

        String targetName = config.getProperty( CIApplication.CONFIGURATION_XCODE_TARGET.getKey(),
                (String) CIApplication.CONFIGURATION_XCODE_TARGET.getDefault() );

        String sdkName = config.getProperty( CIApplication.CONFIGURATION_XCODE_SDK.getKey(),
                (String) CIApplication.CONFIGURATION_XCODE_SDK.getDefault() );

        int result = -1;
        Writer buildOut = null;
        Process process = null;
        StreamGobbler serr = null, sout = null;
        try
        {
            buildOut = new FileWriter( output );

            // execute a clean first of all
            ArrayList<String> commands = new ArrayList<String>();
            commands.add( "xcodebuild" );
            commands.add( "clean" );

            process = Runtime.getRuntime().exec( commands.toArray( new String[2] ), null, dir );

            serr = new StreamGobbler( new InputStreamReader( process.getErrorStream() ), buildOut );
            sout = new StreamGobbler( new InputStreamReader( process.getInputStream() ), buildOut );
            serr.start();
            sout.start();

            result = process.waitFor();

            if ( result == 0 )
            {
                // defensively try to close the gobblers
                // check that our gobblers are finished...
                while ( !serr.isComplete() || !sout.isComplete() )
                {
                    log.debug( "waiting 1s to close gobbler" );
                    try
                    {
                        Thread.sleep( 1000 );
                    }
                    catch ( InterruptedException e )
                    {
                        // we were just trying to tidy up...
                    }
                }

                IOUtil.close( process.getOutputStream() );
                IOUtil.close( process.getErrorStream() );
                IOUtil.close( process.getInputStream() );
                process.destroy();

                commands.clear();
                commands.add( "xcodebuild" );
                // build the specified configuration, or default if none specified
                if ( !StringUtil.isEmpty( confName ) )
                {
                    commands.add( "-configuration" );
                    commands.add( confName );
                }

                // build the specified target if specified
                if ( !StringUtil.isEmpty( targetName ) )
                {
                    commands.add( "-target" );
                    commands.add( targetName );
                }

                // link to the specified sdk if specified
                if ( !StringUtil.isEmpty( sdkName ) )
                {
                    commands.add( "-sdk" );
                    commands.add( sdkName );
                }

                process = Runtime.getRuntime().exec( commands.toArray( new String[commands.size()] ), null, dir );

                serr = new StreamGobbler( new InputStreamReader( process.getErrorStream() ), buildOut );
                sout = new StreamGobbler( new InputStreamReader( process.getInputStream() ), buildOut );
                serr.start();
                sout.start();

                result = process.waitFor();
            }

        }
        catch ( InterruptedException e )
        {
            // TODO use this hook when we cancel the process
        }
        catch ( IOException e )
        {
            e.printStackTrace( new PrintWriter( buildOut ) );
            log.error( "Unable to write to build output file - reported in build log", e );
        }
        finally
        {
            if ( process != null )
            {
                // defensively try to close the gobblers
                if ( serr != null && sout != null )
                {
                    // check that our gobblers are finished...
                    while ( !serr.isComplete() || !sout.isComplete() )
                    {
                        log.debug( "waiting 1s to close gobbler" );
                        try
                        {
                            Thread.sleep( 1000 );
                        }
                        catch ( InterruptedException e )
                        {
                            // we were just trying to tidy up...
                        }
                    }
                }

                IOUtil.close( process.getOutputStream() );
                IOUtil.close( process.getErrorStream() );
                IOUtil.close( process.getInputStream() );
                process.destroy();
            }
        }

        parseDatFiles( dir, build );

        build.setEndTime( new Date() );
        if ( result != 0 )
        {
            build.setStatus( Build.BUILD_FAILED );
        }
        else
        {
            build.setStatus( Build.BUILD_SUCCEEDED );
        }

        IOUtil.close( buildOut );
    }

    protected static void parseDatFiles( File dir, Build build )
    {
        if ( dir.isDirectory() )
        {
            if ( dir.listFiles() != null )
            {
                for ( File file : dir.listFiles() )
                {
                    parseDatFiles( file, build );
                }
            }
        }
        else if ( dir.getName().endsWith( ".dat" ) )
        {
            try
            {
                parseDatFile( dir, build );
            }
            catch ( IOException e )
            {
                System.err.println( "Failure in parsing build .dat file: " + dir.getName() + " (" + e.getMessage() + ")" );
            }
        }
    }

    protected static void parseDatFile( File dat, Build build )
        throws IOException
    {
        BufferedReader reader = new BufferedReader( new FileReader( dat ) );
        String rootPath = Manager.getStorageInstance().getDataDirectory().getAbsolutePath();
        HibernateStorage storage = (HibernateStorage) Manager.getStorageInstance();

        String line;
        long totalMillis = 0;
        TestResultSet currentSuite = null;
        int setTests = 0, failedTests = 0;
        int totalTests = 0, totalFailed = 0;
        while ( ( line = reader.readLine() ) != null )
        {
            if ( line.length() > 1 && line.charAt( 0 ) == 'o' )
            {
                if ( line.startsWith( "oTest Suite" ) && !line.contains( rootPath ) )
                {
                    int pos1 = line.indexOf( "\'" );
                    int pos2 = line.indexOf( "\'", pos1 + 1 );
                    String suiteName = line.substring( pos1 + 1, pos2 );

                    if ( line.contains( " started at " ) )
                    {

                        currentSuite = new TestResultSet( suiteName, null );
                    }
                    else if ( line.contains( " finished at " ) )
                    {
                        if ( currentSuite == null )
                        {
                            System.err.println( "No Test Suite to close for '" + suiteName + "'" );
                            continue;
                        }

                        currentSuite.setTests( setTests );
                        currentSuite.setFailures( failedTests );
                        currentSuite.setDuration( totalMillis );

                        storage.save( currentSuite );
                        build.getTestResults().add( currentSuite );
                        build.setTests( totalTests );
                        build.setFailures( totalFailed );

                        // reset for next test
                        currentSuite = null;
                        setTests = failedTests = 0;
                        totalMillis = 0;
                    }
                }
                else if ( line.startsWith( "oTest Case" ) && !line.endsWith( "started." ) )
                {
                    int pos1 = line.indexOf( "\'" );
                    int pos2 = line.indexOf( "\'", pos1 + 1 );
                    String testName = line.substring( pos1 + 1, pos2 );

                    if ( currentSuite == null )
                    {
                        System.err.println( "No Test Suite for test '" + testName + "'" );
                        continue;
                    }

                    pos1 = line.indexOf( "(", pos2 );
                    pos2 = line.indexOf( " ", pos1 + 1 );
                    String testTime = line.substring( pos1 + 1, pos2 );
                    long millis = (long) ( Double.parseDouble( testTime ) * 1000 );
                    totalMillis += millis;

                    setTests++;
                    totalTests++;
                    int status;// = TestResult.STATUS_ERROR;
                    if ( line.contains( " passed (" ) )
                    {
                        status = TestResult.STATUS_PASSED;
                    }
                    else
                    {
                        status = TestResult.STATUS_FAILED;
                        failedTests++;
                        totalFailed++;
                    }

                    TestResult testResult = new TestResult( testName, status, millis, "", null );
                    storage.save( testResult );
                    currentSuite.getResults().add( testResult );
                }
                else if ( line.startsWith( "oExecuted" ) )
                {
                    // don't think we need this
                }
            }
        }

        IOUtil.close( reader );
    }
}
