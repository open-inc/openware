package de.openinc.ow.middleware.consumer;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.middleware.services.DataService;

public class FileConsumer {
	private List<String> pathToBeWatched;
	private FileWatchRunner runner;
	private Thread thread;

	public FileConsumer(List<String> paths) {
		this.pathToBeWatched = paths;
	}

	public void startWatching() {
		if (runner == null || (thread != null && !thread.isAlive())) {
			runner = new FileWatchRunner(pathToBeWatched);
			thread = new Thread(runner);
			thread.start();
		}
	}

	public void stopWatching() {
		if (runner != null)
			runner.stop();
	}

}

class FileWatchRunner implements Runnable {
	private WatchService watcher;
	private HashMap<WatchKey, Path> pathToBeWatched;

	public FileWatchRunner(List<String> paths) {
		try {
			watcher = FileSystems.getDefault().newWatchService();
			pathToBeWatched = new HashMap<>();
		} catch (IOException e) {
			OpenWareInstance.getInstance().logError(this.getClass().getName() + ":" +
													e.getMessage());
			e.printStackTrace();
		}

		if (watcher != null) {
			for (String path : paths) {
				Path dir = Paths.get(path);
				try {
					WatchKey key = dir.register(watcher, ENTRY_CREATE
					/* ENTRY_MODIFY */
					);
					pathToBeWatched.put(key, dir);
				} catch (IOException x) {
					System.err.println(x);
				}

			}
		}
	}

	public void run() {
		for (;;) {

			// wait for key to be signaled
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException x) {
				return;
			} catch (ClosedWatchServiceException ex) {
				OpenWareInstance.getInstance().logInfo("FileWatcher stopped");
				return;
			}

			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind<?> kind = event.kind();

				// This key is registered only
				// for ENTRY_CREATE events,
				// but an OVERFLOW event can
				// occur regardless if events
				// are lost or discarded.
				if (kind == OVERFLOW) {
					continue;
				}

				// The filename is the
				// context of the event.

				WatchEvent<Path> ev = (WatchEvent<Path>) event;
				Path filename = ev.context();
				Path file = Paths.get(
						pathToBeWatched.get(key).toFile().getAbsoluteFile() + File.separator + filename.getFileName());
				OpenWareInstance.getInstance().logInfo("" + kind +
														": " +
														file.toAbsolutePath().toString());
				CSVFileReader csvreader = new CSVFileReader(file);
				new Thread(csvreader).start();

			}

			// Reset the key -- this step is critical if you want to
			// receive further watch events. If the key is no longer valid,
			// the directory is inaccessible so exit the loop.
			boolean valid = key.reset();
			if (!valid) {
				break;
			}
		}

	}

	public void stop() {
		if (watcher != null)
			try {
				watcher.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

}

class CSVFileReader implements Runnable {

	Path toRead;

	public CSVFileReader(Path path) {
		toRead = path;
	}

	@Override
	public void run() {
		OpenWareInstance.getInstance().logInfo("Reading CSV File: " + toRead.toAbsolutePath().toString());
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			BufferedReader br = Files.newBufferedReader(toRead);
			String data = "";
			String line;
			boolean first = true;
			while ((line = br.readLine()) != null) {
				if (first) {
					data += (line);
					first = false;
				} else {
					data += ("\n" + line);
				}

			}
			br.close();
			OpenWareInstance.getInstance().logInfo("File " + toRead.toAbsolutePath().toString() +
													" was read!");
			DataService.onNewData(toRead.toAbsolutePath().toFile().getParentFile().getName(), data);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
