package com.imyash.pesuwifi

import com.imyash.pesuwifi.data.PortalApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalApiTest {

    @Test
    fun testParseXmlLiveAck() {
        val xml = "<response><ack>ack</ack></response>"
        val parsed = PortalApi.parseXml(xml)
        assertEquals("ack", parsed["ack"])
    }

    @Test
    fun testParseXmlLoginSuccess() {
        val xml = "<response><status>LIVE</status><message>You are signed in as {username}</message></response>"
        val parsed = PortalApi.parseXml(xml)
        assertEquals("LIVE", parsed["status"])
        assertTrue(parsed["message"]?.contains("signed in") == true)
    }

    @Test
    fun testParseXmlLoginFailure() {
        val xml = "<response><status>LOGIN</status><message>The system could not log you on. Make sure your password is correct</message></response>"
        val parsed = PortalApi.parseXml(xml)
        assertEquals("LOGIN", parsed["status"])
        assertTrue(parsed["message"]?.contains("could not log you on") == true)
    }

    @Test
    fun testParseXmlLogout() {
        val xml = "<response><status>LOGIN</status><message>You've signed out</message></response>"
        val parsed = PortalApi.parseXml(xml)
        assertEquals("LOGIN", parsed["status"])
        assertEquals("You've signed out", parsed["message"])
    }

    @Test
    fun testParseXmlWithCdata() {
        val xml = "<requestresponse><status>LOGIN</status><message><![CDATA[The system could not log you on. Make sure your password is correct]]></message></requestresponse>"
        val parsed = PortalApi.parseXml(xml)
        assertEquals("LOGIN", parsed["status"])
        assertEquals("The system could not log you on. Make sure your password is correct", parsed["message"])
    }

    @Test
    fun testParseXmlDataLimitExceededCdata() {
        val xml = "<requestresponse><status>LOGIN</status><message><![CDATA[Your data limit has exceeded]]></message></requestresponse>"
        val parsed = PortalApi.parseXml(xml)
        assertEquals("LOGIN", parsed["status"])
        assertEquals("Your data limit has exceeded", parsed["message"])
    }

    @Test
    fun testParseXmlMaxLoginLimitCdata() {
        val xml = "<requestresponse><status>LOGIN</status><message><![CDATA[Maximum Login Limit Reached]]></message></requestresponse>"
        val parsed = PortalApi.parseXml(xml)
        assertEquals("LOGIN", parsed["status"])
        assertEquals("Maximum Login Limit Reached", parsed["message"])
    }

    @Test
    fun testParseXmlLiveRequestResponseAck() {
        val xml = "<?xml version='1.0' ?><liverequestresponse><ack><![CDATA[ack]]></ack><livemessage><![CDATA[]]></livemessage></liverequestresponse>"
        val parsed = PortalApi.parseXml(xml)
        val ack = parsed["ack"]?.trim()?.lowercase() ?: ""
        assertEquals("ack", ack)
        assertTrue(ack == "ack")
    }

    @Test
    fun testParseXmlLiveRequestResponseLiveOff() {
        val xml = "<?xml version='1.0' ?><liverequestresponse><ack><![CDATA[live_off]]></ack><livemessage><![CDATA[]]></livemessage></liverequestresponse>"
        val parsed = PortalApi.parseXml(xml)
        val ack = parsed["ack"]?.trim()?.lowercase() ?: ""
        assertEquals("live_off", ack)
        org.junit.Assert.assertFalse(ack == "ack")
    }

    @Test
    fun testParseXmlLiveRequestResponseNack() {
        val xml = "<?xml version='1.0' ?><liverequestresponse><ack><![CDATA[nack]]></ack><livemessage><![CDATA[User already logged in]]></livemessage></liverequestresponse>"
        val parsed = PortalApi.parseXml(xml)
        val ack = parsed["ack"]?.trim()?.lowercase() ?: ""
        assertEquals("nack", ack)
        org.junit.Assert.assertFalse(ack == "ack")
    }

    @Test
    fun testCampusSsidsExcludesDeletedAndIncludesRequired() {
        val ssids = com.imyash.pesuwifi.data.PortalRepository.CAMPUS_SSIDS
        assertTrue(ssids.contains("AMAATRA-HOSTEL"))
        assertTrue(ssids.contains("PESU-EC-Campus"))
        assertTrue(ssids.contains("PESU-RR-Campus"))
        assertTrue(ssids.contains("PESU-CIE"))
        assertTrue(ssids.contains("Foodcourt"))
        assertTrue(ssids.contains("pes south cafe"))

        org.junit.Assert.assertFalse(ssids.contains("PESU-Campus"))
        org.junit.Assert.assertFalse(ssids.contains("PESU-WiFi"))
        org.junit.Assert.assertFalse(ssids.contains("PES_WIFI"))
        org.junit.Assert.assertFalse(ssids.contains("AMAATRA_HOSTEL"))
    }

    @Test
    fun testWifiSuggestionTargets() {
        val targets = com.imyash.pesuwifi.data.WifiSuggestionManager.CAMPUS_TARGETS
        val amaatra = targets.firstOrNull { it.ssid == "AMAATRA-HOSTEL" && it.passphrase != null }
        assertEquals("SouthPe$!t", amaatra?.passphrase)

        val foodcourt = targets.firstOrNull { it.ssid == "Foodcourt" && it.passphrase != null }
        assertEquals("PESU-EC-Campus", foodcourt?.passphrase)

        val cafe = targets.firstOrNull { it.ssid == "pes south cafe" && it.passphrase != null }
        assertEquals("PESU-EC-Campus", cafe?.passphrase)
    }

    @Test
    fun testPortalBaseUrlDerivation() {
        PortalApi.portalBaseUrl = "http://192.168.1.1:8090"
        assertEquals("http://192.168.1.1:8090/login.xml", PortalApi.loginUrl)
        assertEquals("http://192.168.1.1:8090/logout.xml", PortalApi.logoutUrl)
        assertEquals("http://192.168.1.1:8090/live", PortalApi.liveUrl)
        assertEquals("http://192.168.1.1:8090/httpclient.html", PortalApi.probeUrl)

        PortalApi.portalBaseUrl = PortalApi.DEFAULT_PORTAL_BASE
        assertEquals("http://192.168.254.1:8090/login.xml", PortalApi.loginUrl)
    }

    @Test
    fun testKeepaliveIntervalWithJitter() {
        assertEquals(120_000L, com.imyash.pesuwifi.service.WifiKeepaliveService.KEEPALIVE_BASE_INTERVAL_MS)
        assertEquals(30_000L, com.imyash.pesuwifi.service.WifiKeepaliveService.KEEPALIVE_JITTER_MS)

        repeat(50) {
            val interval = com.imyash.pesuwifi.service.WifiKeepaliveService.getNextKeepaliveIntervalMs()
            assertTrue("Interval $interval should be >= 90000ms", interval >= 90_000L)
            assertTrue("Interval $interval should be <= 150000ms", interval <= 150_000L)
        }
    }
}
