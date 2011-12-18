/**
 * Copyright (c) 2010-2011 Martin M Reed
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
package org.hardisonbrewing.maven.cxx.qde;

import generated.net.rim.bar.Asset;
import generated.net.rim.bar.AssetConfiguration;
import generated.net.rim.bar.Qnx;
import generated.org.eclipse.cdt.StorageModule.Configuration;

import java.io.File;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.hardisonbrewing.maven.core.FileUtils;
import org.hardisonbrewing.maven.core.JoJoMojoImpl;
import org.hardisonbrewing.maven.cxx.ProjectService;

/**
 * @goal qde-generate-bar-descriptor
 * @phase qde-generate-bar-descriptor
 */
public class GenerateBarDescriptorMojo extends JoJoMojoImpl {

    /**
     * @parameter
     */
    public String target;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File barDescriptorInput = BarDescriptorService.getBarDescriptorFile();
        Qnx barDescriptor = BarDescriptorService.readBarDescriptor( barDescriptorInput );

        updateAssets( barDescriptor.getAsset() );

        Configuration configuration = CProjectService.getBuildConfiguration( target );
        String configurationId = configuration.getId();

        List<AssetConfiguration> assetConfigurations = barDescriptor.getConfiguration();
        if ( assetConfigurations != null ) {
            for (int i = assetConfigurations.size() - 1; i >= 0; i--) {
                AssetConfiguration assetConfiguration = assetConfigurations.get( i );
                if ( configurationId.equals( assetConfiguration.getId() ) ) {
                    updateAssets( assetConfiguration.getAsset() );
                }
                else {
                    assetConfigurations.remove( i );
                }
            }
        }

        File barDescriptorOutput = TargetDirectoryService.getBarDescriptorFile();
        BarDescriptorService.writeBarDescriptor( barDescriptor, barDescriptorOutput );
    }

    private final void updateAssets( List<Asset> assets ) {

        if ( assets == null ) {
            return;
        }

        for (Asset asset : assets) {
            updateAsset( asset );
        }
    }

    private final void updateAsset( Asset asset ) {

        String path = asset.getPath();
        path = PropertiesService.populateTemplateVariables( path, "${", "}" );

        if ( FileUtils.isCanonical( path ) ) {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append( ProjectService.getBaseDirPath() );
            stringBuffer.append( File.separator );
            stringBuffer.append( path );
            path = stringBuffer.toString();
        }

        asset.setPath( path );
    }
}