/*
 * Copyright 2008-2014 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bull.javamelody;

import static net.bull.javamelody.HttpParameters.PERIOD_PARAMETER;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Gestionnaire de cookie http persistent.
 * @author Emeric Vernat
 */
class HttpCookieManager {
	private static final String PERIOD_COOKIE_NAME = "javamelody.period";

	private static final String CUSTOM_PERIOD = "custom.period";

	// période par défaut : jour
	private static Range defaultRange = Period.JOUR.getRange();

	Range getRange(HttpServletRequest req, HttpServletResponse resp) {
		final Range range;
		if (req.getParameter(PERIOD_PARAMETER) == null) {
			// pas de paramètre period dans la requête, on cherche le cookie
			final Cookie cookie = getCookieByName(req, PERIOD_COOKIE_NAME);
			if (cookie == null) {
				// pas de cookie, période par défaut
				range = defaultRange;
			} else {
				range = Range.parse(cookie.getValue());
			}
		} else {
			range = Range.parse(req.getParameter(PERIOD_PARAMETER));
			// un paramètre period est présent dans la requête :
			// l'utilisateur a choisi une période, donc on fixe le cookie
			addCookie(req, resp, PERIOD_COOKIE_NAME, range.getValue());
			addCookie(req, resp, CUSTOM_PERIOD, null);

		}

		if (req.getParameter(CUSTOM_PERIOD) != null) {
			addCookie(req, resp, CUSTOM_PERIOD, req.getParameter(CUSTOM_PERIOD));
		}
		Cookie customPeriod = getCookieByName(req, CUSTOM_PERIOD);
		if (customPeriod == null || customPeriod.getValue() == null
				|| customPeriod.getValue().trim().equals("")) {
			return range;
		}

		String customPeriodValue = customPeriod.getValue();
		int index = customPeriodValue.indexOf(Range.CUSTOM_PERIOD_SEPARATOR);
		try {
			Date startDate = new SimpleDateFormat("dd/MM/yyyy-HH:mm:ss").parse(customPeriodValue
					.substring(0, index));
			Date endDate = new SimpleDateFormat("dd/MM/yyyy-HH:mm:ss").parse(customPeriodValue
					.substring(index + 1));
			return range.customPeriod(startDate, endDate);
		} catch (ParseException e) {
			throw new IllegalArgumentException("Could not parse custom date", e);
		}

	}

	Cookie getCookieByName(HttpServletRequest req, String cookieName) {
		final Cookie[] cookies = req.getCookies();
		if (cookies != null) {
			for (final Cookie cookie : cookies) {
				if (cookieName.equals(cookie.getName())) {
					return cookie;
				}
			}
		}
		return null;
	}

	void addCookie(HttpServletRequest req, HttpServletResponse resp, String cookieName,
			String cookieValue) {
		if (!"added".equals(req.getAttribute(cookieName))) {
			final Cookie cookie = new Cookie(cookieName, cookieValue);
			// cookie persistant, valide pendant 30 jours
			cookie.setMaxAge(30 * 24 * 60 * 60);
			// inutile d'envoyer ce cookie aux autres URLs que le monitoring
			cookie.setPath(req.getRequestURI());
			resp.addCookie(cookie);
			req.setAttribute(cookieName, "added");
		}
	}

	String getCookiesAsString(HttpServletRequest req) {
		final Cookie[] cookies = req.getCookies();
		if (cookies != null) {
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < cookies.length; i++) {
				final Cookie cookie = cookies[i];
				sb.append(cookie.getName()).append('=').append(cookie.getValue());
				if (i < cookies.length - 1) {
					sb.append("; ");
				}
			}
			return sb.toString();
		}
		return null;
	}

	static void setDefaultRange(Range range) {
		defaultRange = range;
	}
}
