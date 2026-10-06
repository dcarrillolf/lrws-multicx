package com.liferay.custom

import groovy.json.JsonSlurper

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

import org.gradle.api.GradleException

class PortalInstancesUtil {

	static List<String> getWebIds(String portalURL, String clientId, String clientSecret) {
		String baseURL = portalURL.replaceAll('/+$', '')

		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

		String form = [client_id: clientId, client_secret: clientSecret, grant_type: "client_credentials"].collect {
			key, value -> key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)
		}.join("&")

		HttpResponse<String> tokenResponse = httpClient.send(
			HttpRequest.newBuilder(
				URI.create(baseURL + "/o/oauth2/token")
			).header(
				"Content-Type", "application/x-www-form-urlencoded"
			).POST(
				HttpRequest.BodyPublishers.ofString(form)
			).build(),
			HttpResponse.BodyHandlers.ofString())

		if (tokenResponse.statusCode() != 200) {
			throw new GradleException(
				"Unable to get an OAuth 2 token from " + baseURL + " (HTTP " + tokenResponse.statusCode() + "): " +
					tokenResponse.body())
		}

		String accessToken = new JsonSlurper().parseText(tokenResponse.body()).access_token

		HttpResponse<String> portalInstancesResponse = httpClient.send(
			HttpRequest.newBuilder(
				URI.create(baseURL + "/o/headless-portal-instances/v1.0/portal-instances?skipDefault=true")
			).header(
				"Accept", "application/json"
			).header(
				"Authorization", "Bearer " + accessToken
			).GET(
			).build(),
			HttpResponse.BodyHandlers.ofString())

		if (portalInstancesResponse.statusCode() != 200) {
			throw new GradleException(
				"Unable to get portal instances from " + baseURL + " (HTTP " +
					portalInstancesResponse.statusCode() + "): " + portalInstancesResponse.body() +
						". The OAuth 2 application must belong to the default virtual instance, use a client " +
							"credentials user that is an administrator of the default virtual instance and " +
								"include the Liferay.Headless.Portal.Instances read scope")
		}

		List<String> webIds = new JsonSlurper().parseText(portalInstancesResponse.body()).items.collect {
			it.portalInstanceId as String
		}

		return ["default"] + webIds.sort()
	}

}
