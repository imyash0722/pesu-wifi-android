package com.imyash.pesuwifi

import com.imyash.pesuwifi.data.PortalApi
import com.imyash.pesuwifi.service.CampusHeartbeatScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusHeartbeatTest {

    @Test
    fun testHeartbeatIntervalConstants() {
        assertEquals(150_000L, CampusHeartbeatScheduler.HEARTBEAT_INTERVAL_MS)
        assertEquals("com.imyash.pesuwifi.ACTION_CAMPUS_HEARTBEAT", CampusHeartbeatScheduler.ACTION_HEARTBEAT)
    }

    @Test
    fun testParseXmlLiveAck() {
        val xml = "<?xml version='1.0' ?><liverequestresponse><ack><![CDATA[ack]]></ack><livemessage><![CDATA[]]></livemessage></liverequestresponse>"
        val parsed = PortalApi.parseXml(xml)
        val ack = parsed["ack"]?.trim()?.lowercase() ?: ""
        assertEquals("ack", ack)
        assertTrue(ack == "ack")
    }

    @Test
    fun testParseXmlLiveOff() {
        val xml = "<?xml version='1.0' ?><liverequestresponse><ack><![CDATA[live_off]]></ack><livemessage><![CDATA[]]></livemessage></liverequestresponse>"
        val parsed = PortalApi.parseXml(xml)
        val ack = parsed["ack"]?.trim()?.lowercase() ?: ""
        assertEquals("live_off", ack)
        assertFalse(ack == "ack")
    }

    @Test
    fun testParseXmlNack() {
        val xml = "<?xml version='1.0' ?><liverequestresponse><ack><![CDATA[nack]]></ack><livemessage><![CDATA[User already logged in]]></livemessage></liverequestresponse>"
        val parsed = PortalApi.parseXml(xml)
        val ack = parsed["ack"]?.trim()?.lowercase() ?: ""
        assertEquals("nack", ack)
        assertFalse(ack == "ack")
    }
}
