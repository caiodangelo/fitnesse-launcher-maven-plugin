package uk.co.javahelp.maven.plugin.fitnesse.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import uk.co.javahelp.maven.plugin.fitnesse.util.Interrupter;
import fitnesse.Shutdown;
import fitnesse.socketservice.SocketService;

/**
 * Goal that launches FitNesse as a wiki server.
 * Useful for manually running / developing / debugging FitNesse tests.
 * Once launched, just visit http://localhost:&lt;port&gt;/&lt;suite&gt;.
 * Use Ctrl+C to shutdown, or send GET to http://localhost:&lt;port&gt;/?responder=shutdown.
 *
 * @goal wiki
 * @requiresDependencyResolution
 */
public class WikiMojo extends AbstractMojo {
	
	private static final String FITNESSE_SOCKET_SERVICE = SocketService.class.getName();

    /**
	 * Unfortunately, the FitNesse API does not expose a way to stop the wiki server programmatically,
	 * except via a sending "/?responder=shutdown" via HTTP, which is what the {@link Shutdown} object does.
	 * The object / method we need access to is {@link fitnesse.FitNesse.stop()}.
	 * This could easily have been returned from our public call
	 * to {@link fitnesseMain.FitNesseMain.launchFitNesse(Arguments)}
	 * <p>
	 * We need to discover the FitNesse thread running (which is not exposed either).
	 * This is not a daemon thead, but we need to join() it all the same,
	 * as Maven calls System.exit() once it's business is done.
	 * The intended use is for the user to press Ctrl+C to quit.
	 */
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
    	final String portString = this.port.toString();
        try {
        	Runtime.getRuntime().addShutdownHook(new Interrupter(Thread.currentThread(), 0L));
            this.fitNesseHelper.launchFitNesseServer(portString, this.workingDir, this.root, this.logDir);
    		if(this.createSymLink) {
	            this.fitNesseHelper.createSymLink(this.suite, this.test, this.project.getBasedir(), this.testResourceDirectory, this.port);
    		}
            final Thread fitnesseThread = findFitNesseServerThread();
            if(fitnesseThread != null) {
            	getLog().info("FitNesse wiki server launched.");
                fitnesseThread.join();
            }
        } catch (InterruptedException e) {
        	getLog().info("FitNesse wiki server interrupted!");
        } catch (Exception e) {
            throw new MojoExecutionException("Exception launching FitNesse", e);
        } finally {
        	this.fitNesseHelper.shutdownFitNesseServer(portString);
        }
    }
    
    private Thread findFitNesseServerThread() {
    	Thread[] activeThreads = findActiveThreads(3);
    	for( int i = activeThreads.length - 1 ; i >= 0 ; i-- ) {
    		final StackTraceElement[] trace = activeThreads[i].getStackTrace();
            for( int j = trace.length - 1 ; j >= 0 ; j-- ) {
            	if(FITNESSE_SOCKET_SERVICE.equals(trace[j].getClassName())) {
            		return activeThreads[i];
            	}
            }
    	}
       	getLog().warn("Could not identify FitNesse service Thread.");
    	return null;
    }
    
    private Thread[] findActiveThreads(int arraySize) {
    	Thread[] activeThreads = new Thread[arraySize];
    	int threadsFound = Thread.currentThread().getThreadGroup().enumerate(activeThreads, false);
    	if(threadsFound < arraySize) {
        	Thread[] foundThreads = new Thread[threadsFound];
        	System.arraycopy(activeThreads, 0, foundThreads, 0, threadsFound);
        	return foundThreads;
    	} 
    	return findActiveThreads(arraySize + arraySize);
    }
}
