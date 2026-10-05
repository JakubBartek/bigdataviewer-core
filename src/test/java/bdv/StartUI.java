package bdv;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import net.imagej.ImageJ;
import ome.zarr.fiji.PyramidalBdv;
import ome.zarr.fiji.plugins.PyramidalService;
import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.metadata.Omero;
import ome.zarr.n5.N5PyramidBackend;

import java.awt.*;
import java.net.URI;
import java.util.List;

import static ome.zarr.fiji.util.BdvUtils.omeroChannels;
import static ome.zarr.fiji.util.BdvUtils.registerBdvWindow;
import static ome.zarr.fiji.util.BdvUtils.setChannelProperties;

public class StartUI
{
	static
	{
		net.imagej.patcher.LegacyInjector.preinit();
	}

	public static void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		if ( args.length == 0 )
		{
			System.out.println( "Please provide at least one URL to OME-Zarr." );
			return;
		}

		final PyramidBackend backend = new N5PyramidBackend();
		final PyramidalService pyramidalService = ij.context().getService( PyramidalService.class );

		final BdvHandle mainBdvHandle = BdvFunctions.show();
		final BdvOptions mainBdvOptions = BdvOptions.options().frameTitle( "LUT business" ).addTo( mainBdvHandle );

		for ( String arg : args )
		{
			System.out.println( "Opening: " + arg );
			final URI uri = URI.create( arg );

//			OmeZarrOpenActions opener = new OmeZarrOpenActions( uri, ij.context() );
//			opener.showInBdv(/* BdvOptions or BdvHandle */);

//			OmeZarr oz = new OmeZarr( uri, ij.context(), backend );
//			oz.showInBdv(/* BdvOptions or BdvHandle */);

			PyramidalBdv< ? > pyramidalBdv = new PyramidalBdv<>( ij.context(), ( PyramidContents ) backend.read( uri ) );
			BdvFunctions.show( pyramidalBdv.asSources(), pyramidalBdv.getPyramidContents().numTimepoints(), mainBdvOptions );
			List< Omero.Channel > omeroChannels = omeroChannels(
					pyramidalBdv.getPyramidContents().omero,
					pyramidalBdv.asSources().size() );
			setChannelProperties( omeroChannels,
					pyramidalBdv.asSources(),
					mainBdvHandle.getConverterSetups(),
					mainBdvHandle.getViewerPanel().state() );
			//
			Container topLevelContainer = mainBdvHandle.getViewerPanel().getRootPane().getParent();
			if ( topLevelContainer instanceof Window )
			{
				Window window = ( Window ) topLevelContainer;
				registerBdvWindow( pyramidalBdv, window, pyramidalService );
			}

		}
	}
}
