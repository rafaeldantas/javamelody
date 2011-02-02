/*
 * Copyright 2008-2010 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Java Melody is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java Melody is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Melody.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bull.javamelody; // NOPMD

import static net.bull.javamelody.HttpParameters.CONTENT_DISPOSITION;
import static net.bull.javamelody.HttpParameters.DATABASE_PART;
import static net.bull.javamelody.HttpParameters.HEAP_HISTO_PART;
import static net.bull.javamelody.HttpParameters.PART_PARAMETER;
import static net.bull.javamelody.HttpParameters.PROCESSES_PART;
import static net.bull.javamelody.HttpParameters.REQUEST_PARAMETER;
import static net.bull.javamelody.HttpParameters.SESSIONS_PART;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Contrôleur au sens MVC de l'ihm de monitoring pour la partie pdf.
 * @author Emeric Vernat
 */
class PdfController {
	private final HttpCookieManager httpCookieManager = new HttpCookieManager();
	private final Collector collector;
	private final CollectorServer collectorServer;

	PdfController(Collector collector, CollectorServer collectorServer) {
		super();
		assert collector != null;
		this.collector = collector;
		this.collectorServer = collectorServer;
	}

	void doPdf(HttpServletRequest httpRequest, HttpServletResponse httpResponse,
			List<JavaInformations> javaInformationsList) throws IOException {
		addPdfContentTypeAndDisposition(httpRequest, httpResponse);
		try {
			final String part = httpRequest.getParameter(PART_PARAMETER);
			if (SESSIONS_PART.equalsIgnoreCase(part)) {
				doSessions(httpResponse);
			} else if (PROCESSES_PART.equalsIgnoreCase(part)) {
				doProcesses(httpResponse);
			} else if (DATABASE_PART.equalsIgnoreCase(part)) {
				final int index = DatabaseInformations.parseRequestIndex(httpRequest
						.getParameter(REQUEST_PARAMETER));
				doDatabase(httpResponse, index);
			} else if (HEAP_HISTO_PART.equalsIgnoreCase(part)) {
				doHeapHisto(httpResponse);
			} else {
				if (!isFromCollectorServer()) {
					// avant de faire l'affichage on fait une collecte,  pour que les courbes
					// et les compteurs par jour soit à jour avec les dernières requêtes
					collector.collectLocalContextWithoutErrors();
				}

				final Range range = httpCookieManager.getRange(httpRequest, httpResponse);
				final PdfReport pdfReport = new PdfReport(collector, isFromCollectorServer(),
						javaInformationsList, range, httpResponse.getOutputStream());
				pdfReport.toPdf();
			}
		} catch (final IOException e) { // NOPMD
			throw e;
		} catch (final Exception e) {
			// ne devrait pas arriver puisque les pdf ne s'affichent normalement pas sans afficher auparavant le html
			throw new IllegalStateException(e);
		} finally {
			httpResponse.getOutputStream().flush();
		}
	}

	private void doSessions(HttpServletResponse httpResponse) throws IOException {
		final PdfOtherReport pdfOtherReport = new PdfOtherReport(collector.getApplication(),
				httpResponse.getOutputStream());
		final List<SessionInformations> sessionsInformations;
		if (!isFromCollectorServer()) {
			sessionsInformations = SessionListener.getAllSessionsInformations();
		} else {
			sessionsInformations = collectorServer.collectSessionInformations(
					collector.getApplication(), null);
		}
		pdfOtherReport.writeSessionInformations(sessionsInformations);
	}

	private void doProcesses(HttpServletResponse httpResponse) throws IOException {
		final PdfOtherReport pdfOtherReport = new PdfOtherReport(collector.getApplication(),
				httpResponse.getOutputStream());
		final List<ProcessInformations> processInformations = ProcessInformations
				.buildProcessInformations();
		pdfOtherReport.writeProcessInformations(processInformations);
	}

	private void doDatabase(HttpServletResponse httpResponse, final int index) throws Exception { // NOPMD
		final DatabaseInformations databaseInformations;
		if (!isFromCollectorServer()) {
			databaseInformations = new DatabaseInformations(index);
		} else {
			databaseInformations = collectorServer.collectDatabaseInformations(
					collector.getApplication(), index);
		}
		final PdfOtherReport pdfOtherReport = new PdfOtherReport(collector.getApplication(),
				httpResponse.getOutputStream());
		pdfOtherReport.writeDatabaseInformations(databaseInformations);
	}

	private void doHeapHisto(HttpServletResponse httpResponse) throws Exception { // NOPMD
		final HeapHistogram heapHistogram;
		if (!isFromCollectorServer()) {
			heapHistogram = VirtualMachine.createHeapHistogram();
		} else {
			heapHistogram = collectorServer.collectHeapHistogram(collector.getApplication());
		}
		final PdfOtherReport pdfOtherReport = new PdfOtherReport(collector.getApplication(),
				httpResponse.getOutputStream());
		pdfOtherReport.writeHeapHistogram(heapHistogram);
	}

	void addPdfContentTypeAndDisposition(HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		// méthode utilisée dans le monitoring Hudson/Jenkins
		httpResponse.setContentType("application/pdf");
		httpResponse.addHeader(
				CONTENT_DISPOSITION,
				encodeFileNameToContentDisposition(httpRequest,
						PdfReport.getFileName(collector.getApplication())));
	}

	private boolean isFromCollectorServer() {
		return collectorServer != null;
	}

	/**
	 * Encode un nom de fichier avec des % pour Content-Disposition, avec téléchargement.
	 * (US-ASCII + Encode-Word : http://www.ietf.org/rfc/rfc2183.txt, http://www.ietf.org/rfc/rfc2231.txt
	 * sauf en MS IE qui ne supporte pas cet encodage et qui n'en a pas besoin)
	 * @param httpRequest HttpServletRequest
	 * @param fileName String
	 * @return String
	 */
	private static String encodeFileNameToContentDisposition(HttpServletRequest httpRequest,
			String fileName) {
		assert fileName != null;
		final String userAgent = httpRequest.getHeader("user-agent");
		if (userAgent != null && userAgent.contains("MSIE")) {
			return "attachment;filename=" + fileName;
		}
		return encodeFileNameToStandardContentDisposition(fileName);
	}

	private static String encodeFileNameToStandardContentDisposition(String fileName) {
		final int length = fileName.length();
		final StringBuilder sb = new StringBuilder(length + length / 4);
		// attachment et non inline pour proposer l'enregistrement (sauf IE6)
		// et non l'affichage direct dans le navigateur
		sb.append("attachment;filename*=\"");
		char c;
		for (int i = 0; i < length; i++) {
			c = fileName.charAt(i);
			if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
				sb.append(c);
			} else {
				sb.append('%');
				if (c < 16) {
					sb.append('0');
				}
				sb.append(Integer.toHexString(c));
			}
		}
		sb.append('"');
		return sb.toString();
	}
}
