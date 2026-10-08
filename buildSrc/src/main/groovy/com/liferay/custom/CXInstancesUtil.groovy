package com.liferay.custom

import groovy.json.JsonGenerator
import groovy.json.JsonSlurper

import java.text.Normalizer

import org.gradle.api.GradleException

class CXInstancesUtil {

	static String formatWebIds(List<String> webIds) {
		if (webIds.any { it.contains(",") || (it != it.trim()) || it.startsWith("[") }) {
			return new JsonGenerator.Options().disableUnicodeEscaping().build().toJson(webIds)
		}

		return webIds.join(",")
	}

	static String escapePropertyValue(String value) {
		StringBuilder sb = new StringBuilder()

		value.toCharArray().eachWithIndex { char c, int index ->
			if (c == (char)'\\') {
				sb.append("\\\\")
			}
			else if ((c == (char)' ') && (index == 0)) {
				sb.append("\\ ")
			}
			else if ((c < (char)0x20) || (c > (char)0x7e)) {
				sb.append(String.format("\\u%04x", (int)c))
			}
			else {
				sb.append(c)
			}
		}

		return sb.toString()
	}

	static List<String> parseWebIds(String envName, Object value) {
		String text = String.valueOf(value).trim()

		List<String> webIds

		if (text.startsWith("[")) {
			def parsed

			try {
				parsed = new JsonSlurper().parseText(text)
			}
			catch (Exception exception) {
				throw new GradleException(
					"Invalid JSON array in cx.build.custom.env.instances[" + envName + "]: " + exception.message)
			}

			if (!(parsed instanceof List)) {
				throw new GradleException("cx.build.custom.env.instances[" + envName + "] must be a JSON array")
			}

			webIds = parsed.collect { String.valueOf(it) }
		}
		else {
			webIds = text.split(",").collect { it.trim() }
		}

		return webIds.findAll { it }.unique()
	}

	static String toIdSuffix(String webId) {
		String suffix = Normalizer.normalize(webId, Normalizer.Form.NFD).replaceAll("\\p{M}", "")

		suffix = suffix.replaceAll("[^A-Za-z0-9]+", "-").replaceAll('^-+|-+$', "")

		if (!suffix) {
			suffix = "instance-" + Integer.toHexString(webId.hashCode())
		}

		return suffix
	}

	static String toBundleSafeName(String webId) {
		String name = Normalizer.normalize(webId, Normalizer.Form.NFD).replaceAll("\\p{M}", "")

		name = name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("[-.]{2,}", "-").replaceAll('^[-.]+|[-.]+$', "")

		if (!name) {
			name = "instance-" + Integer.toHexString(webId.hashCode())
		}

		return name
	}

	static void validateForZipRegistration(String webId) {
		if (webId.find(/[()*\\]/)) {
			throw new GradleException(
				"Web ID \"" + webId + "\" cannot be registered from a client extension zip: Liferay builds an OSGi " +
					"filter with it without escaping \"(\", \")\", \"*\" and \"\\\". Use generateCXConfig instead")
		}
	}

}
