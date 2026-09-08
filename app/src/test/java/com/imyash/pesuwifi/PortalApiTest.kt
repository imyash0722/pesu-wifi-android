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
}
