package org.jorge.lolin1dp;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jorge.lolin1dp.data.DataUpdater;
import org.jorge.lolin1dp.services.NewsService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This file is part of lolin1dp-data-provider.
 * <p/>
 * lolin1dp-data-provider is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * <p/>
 * lolin1dp-data-provider is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p/>
 * You should have received a copy of the GNU General Public License along with
 * lolin1dp-data-provider. If not, see <http://www.gnu.org/licenses/>.
 */
public class Main {

	private static final long UPDATE_FREQUENCY_SECONDS = 60 * 60 * 1;

	public static void main(String[] args) throws Exception {
		String webPort = System.getenv("PORT");
		if ((webPort == null) || webPort.isEmpty()) {
			webPort = "8080";
		}

		System.out.print("Initializing file structure...");
		DataUpdater.initFileStructure();
		System.out.println("done.");

		Server server = new Server(Integer.valueOf(webPort));
		ServletContextHandler context = new ServletContextHandler(
				ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new NewsService()),
				"/services/news/");
		// TODO Uncomment this to export all services
		// context.addServlet(new ServletHolder(new CommunityService()),
		// "/services/community/");
		// context.addServlet(new ServletHolder(new SchoolService()),
		// "/services/school/");

		System.out.print("Requesting server start...");
		server.start();
		System.out.println("done.");

		ScheduledExecutorService updateService = Executors
				.newScheduledThreadPool(1);
		updateService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				System.out.print("Requesting data update...");
				DataUpdater.updateData();
				System.out.println("done.");
			}
		}, 0, Main.UPDATE_FREQUENCY_SECONDS, TimeUnit.SECONDS);

		server.join();
	}
}
