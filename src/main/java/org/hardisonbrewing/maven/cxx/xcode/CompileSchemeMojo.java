/**
 * Copyright (c) 2010-2013 Martin M Reed
 * Copyright (c) 2013 Todd Grooms
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.hardisonbrewing.maven.cxx.xcode;

import generated.xcode.BuildableReference;
import generated.xcode.Scheme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.hardisonbrewing.maven.core.FileUtils;
import org.hardisonbrewing.maven.core.cli.LogStreamConsumer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @goal xcode-compile-scheme
 * @phase compile
 */
public final class CompileSchemeMojo extends AbstractCompileMojo {

    private static final String ARCHIVE_ACTION = "ArchiveAction";
    private static final String POST_ACTIONS = "PostActions";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if ( scheme == null ) {
            getLog().info( "Scheme not specified, skipping." );
            return;
        }

        try {

            File schemeFile = XCodeService.findXcscheme( scheme );
            boolean expectedScheme = XCodeService.isExpectedScheme( scheme, schemeFile.getPath() );
            File schemeTmpFile = null;

//            getLog().info( "schemeFile[" + schemeFile.getPath() + "], expectedScheme[" + expectedScheme + "]" );

            try {

                if ( expectedScheme ) {
                    File targetDirectory = TargetDirectoryService.getTargetDirectory();
                    schemeTmpFile = new File( targetDirectory, schemeFile.getName() );
                    FileUtils.copyFile( schemeFile, schemeTmpFile );
                }
                else {

                    schemeTmpFile = new File( XCodeService.getUserDataDirPath() );
                    schemeTmpFile.mkdir();

                    File userFile = schemeFile;

                    schemeFile = new File( XCodeService.getSchemePath( scheme, false ) );
                    FileUtils.ensureParentExists( schemeFile.getPath() );

                    FileUtils.copyFile( userFile, schemeFile );
                }

                List<String> cmd = buildCommand( scheme );
                Properties buildSettings = loadBuildSettings( cmd );
                PropertiesService.storeBuildSettings( buildSettings, scheme );

                injectPostAction( scheme, schemeFile, buildSettings );

                // copy the build output to a log file that can be used for unit test result parsing
                File buildLogFile = TargetDirectoryService.getBuildLogFile();
                getLog().info( "Saving build log to file: " + buildLogFile );

                OutputStream outputStream = null;

                try {

                    outputStream = new FileOutputStream( TargetDirectoryService.getBuildLogFile() );
                    StreamConsumer systemOut = new LogCopyStreamConsumer( outputStream, LogStreamConsumer.LEVEL_INFO );
                    StreamConsumer systemErr = new LogCopyStreamConsumer( outputStream, LogStreamConsumer.LEVEL_ERROR );
                    execute( cmd, systemOut, systemErr );
                }
                finally {
                    IOUtil.close( outputStream );
                }
            }
            finally {
                if ( expectedScheme ) {
                    FileUtils.copyFile( schemeTmpFile, schemeFile );
                }
                else {
                    FileUtils.deleteDirectory( schemeTmpFile );
                }
            }
        }
        catch (Exception e) {
            throw new IllegalStateException( e );
        }
    }

    private void injectPostAction( String schemeName, File file, Properties buildSettings ) throws Exception {

        Scheme scheme = SchemeService.unmarshal( file );
        Document document = loadSchemeDocument( file );

        injectPostAction( schemeName, scheme, document, "BuildAction" );
        injectPostAction( schemeName, scheme, document, ARCHIVE_ACTION );

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform( new DOMSource( document ), new StreamResult( file ) );
    }

    private void injectPostAction( String schemeName, Scheme scheme, Document document, String action ) throws Exception {

        Element root = (Element) document.getFirstChild();

        NodeList archiveActions = root.getElementsByTagName( action );
        Element archiveAction = (Element) archiveActions.item( 0 );

        Element postActions;

        NodeList _postActions = archiveAction.getElementsByTagName( POST_ACTIONS );
        if ( _postActions.getLength() > 0 ) {
            postActions = (Element) _postActions.item( 0 );
        }
        else {
            postActions = document.createElement( POST_ACTIONS );
            archiveAction.appendChild( postActions );
        }

        BuildableReference[] buildableReferences;

        if ( ARCHIVE_ACTION.equals( action ) ) {

//            Properties buildSettings = PropertiesService.getBuildSettings( schemeName );
//            String target = (String) buildSettings.get( "TARGET_NAME" );
//
//            BuildableReference buildableReference = SchemeService.getBuildableReference( scheme, target );
//
//            Element postAction = createPostAction( document, buildableReference, schemeName );
//            postActions.appendChild( postAction );

            buildableReferences = SchemeService.getBuildableReferences( scheme, true );
        }
        else {
            buildableReferences = SchemeService.getBuildableReferences( scheme, false );
        }

        for (BuildableReference buildableReference : buildableReferences) {
            String target = buildableReference.getBlueprintName();
            Element postAction = createPostAction( document, buildableReference, target );
            postActions.appendChild( postAction );
        }
    }

    private Document loadSchemeDocument( File file ) throws ParserConfigurationException {

        FullScheme fullScheme = SchemeService.unmarshal( file, FullScheme.class );

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.newDocument();

        Element root = document.createElement( "Scheme" );
        document.appendChild( root );

        for (Object object : fullScheme.any) {
            Element element = (Element) object;
            Node node = document.adoptNode( element );
            root.appendChild( node );
        }

        for (Map.Entry<QName, String> attribute : fullScheme.attributes.entrySet()) {
            root.setAttribute( attribute.getKey().toString(), attribute.getValue() );
        }

        return document;
    }

    private Element createPostAction( Document document, BuildableReference buildableReference, String target ) throws Exception {

        String script = "env > \"" + PropertiesService.getBuildEnvironmentPropertiesPath( target ) + "\"";

        Element executionAction = document.createElement( "ExecutionAction" );
        executionAction.setAttribute( "ActionType", "Xcode.IDEStandardExecutionActionsCore.ExecutionActionType.ShellScriptAction" );

        Element actionContent = document.createElement( "ActionContent" );
        actionContent.setAttribute( "title", "Run Script" );
        actionContent.setAttribute( "scriptText", script );
        executionAction.appendChild( actionContent );

        Element environmentBuildable = document.createElement( "EnvironmentBuildable" );
        actionContent.appendChild( environmentBuildable );

        Element _buildableReference = createBuildableReference( document, buildableReference );
        environmentBuildable.appendChild( _buildableReference );

        return executionAction;
    }

    private Element createBuildableReference( Document document, BuildableReference buildableReference ) {

        Element element = document.createElement( "BuildableReference" );
        element.setAttribute( "BuildableIdentifier", buildableReference.getBuildableIdentifier() );
        element.setAttribute( "BlueprintIdentifier", buildableReference.getBlueprintIdentifier() );
        element.setAttribute( "BuildableName", buildableReference.getBuildableName() );
        element.setAttribute( "BlueprintName", buildableReference.getBlueprintName() );
        element.setAttribute( "ReferencedContainer", buildableReference.getReferencedContainer() );
        return element;
    }

    @XmlAccessorType( XmlAccessType.FIELD )
    @XmlType( name = "", propOrder = { "any" } )
    @XmlRootElement( name = "Scheme" )
    public static class FullScheme {

        @XmlAnyElement( lax = true )
        public List<Object> any;

        @XmlAnyAttribute
        public final Map<QName, String> attributes = new HashMap<QName, String>();
    }
}
