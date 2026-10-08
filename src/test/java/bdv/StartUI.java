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

	/** How long the JVM's shutdown hooks get before {@link #haltIfShutdownHangs()} cuts them off. */
	private static final long SHUTDOWN_GRACE_MILLIS = 3000;

	/**
	 * On Windows {@code System.exit} (IntelliJ's Stop) can hang: AWT's shutdown
	 * hook stops the toolkit thread the EDT needs, while SciJava's hook waits on
	 * the EDT. The hook only starts a daemon timer, so a clean exit is not
	 * delayed.
	 */
	private static void haltIfShutdownHangs()
	{
		Runtime.getRuntime().addShutdownHook( new Thread( () -> {
			final Thread timer = new Thread( () -> {
				try
				{
					Thread.sleep( SHUTDOWN_GRACE_MILLIS );
				}
				catch ( final InterruptedException e )
				{
					return;
				}
				Runtime.getRuntime().halt( 1 );
			}, "StartUI shutdown watchdog" );
			timer.setDaemon( true );
			timer.start();
		} ) );
	}

	public static void main( String[] args )
	{
		haltIfShutdownHangs();

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
