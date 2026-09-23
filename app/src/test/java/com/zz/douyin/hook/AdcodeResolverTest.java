package com.zz.douyin.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AdcodeResolverTest {
    @Test
    public void normalizeExpandsShortCodes() {
        assertEquals("110000", AdcodeResolver.normalize("11"));
        assertEquals("110100", AdcodeResolver.normalize("1101"));
        assertEquals("110101", AdcodeResolver.normalize("110101"));
        assertEquals("110101", AdcodeResolver.normalize("  110101  "));
    }

    @Test
    public void normalizeRejectsBadInput() {
        assertNull(AdcodeResolver.normalize(null));
        assertNull(AdcodeResolver.normalize(""));
        assertNull(AdcodeResolver.normalize("   "));
        assertNull(AdcodeResolver.normalize("成都市"));
        assertNull(AdcodeResolver.normalize("11010A"));
        assertNull(AdcodeResolver.normalize("110"));
        assertNull(AdcodeResolver.normalize("11010"));
        assertNull(AdcodeResolver.normalize("1101011"));
        assertNull(AdcodeResolver.normalize("00"));
        assertNull(AdcodeResolver.normalize("000000"));
        assertFalse(AdcodeResolver.isCode("北京市"));
        assertTrue(AdcodeResolver.isCode("110101"));
    }

    @Test
    public void resolveOfflineFindsExactEntries() {
        assertEquals("北京市", AdcodeResolver.resolveOffline("110000"));
        assertEquals("北京市 东城区", AdcodeResolver.resolveOffline("110101"));
        assertEquals("上海市 浦东新区", AdcodeResolver.resolveOffline("310115"));
        assertEquals("广东省 东莞市", AdcodeResolver.resolveOffline("441900"));
        assertEquals("广东省 深圳市", AdcodeResolver.resolveOffline("440300"));
        assertEquals("香港特别行政区", AdcodeResolver.resolveOffline("810000"));
        assertEquals("台湾省", AdcodeResolver.resolveOffline("710000"));
    }

    @Test
    public void resolveOfflineFallsBackToCityAndProvince() {
        assertEquals("北京市 东城区", AdcodeResolver.resolveOffline("110101"));
        assertEquals("北京市", AdcodeResolver.resolveOffline("110199"));
        assertEquals("河北省 石家庄市", AdcodeResolver.resolveOffline("130199"));
        assertEquals("河北省", AdcodeResolver.resolveOffline("139900"));
    }

    @Test
    public void resolveOfflineMissesUnknownCodes() {
        assertEquals("", AdcodeResolver.resolveOffline("999999"));
        assertEquals("", AdcodeResolver.resolveOffline("成都市"));
        assertEquals("", AdcodeResolver.resolveOffline(""));
        assertEquals("", AdcodeResolver.resolveOffline(null));
    }

    @Test
    public void parseIp33ResponseJoinsParts() {
        assertEquals(
                "北京市 东城区",
                AdcodeResolver.parseIp33Response(
                        "{\"province\":\"北京市\",\"city\":\"北京市\","
                                + "\"county\":\"东城区\"}"));
        assertEquals(
                "广东省 深圳市",
                AdcodeResolver.parseIp33Response(
                        "{\"province\" : \"广东省\" , \"city\":\"深圳市\","
                                + "\"county\":\"\"}"));
        assertEquals("", AdcodeResolver.parseIp33Response("{}"));
        assertEquals("", AdcodeResolver.parseIp33Response(""));
        assertEquals("", AdcodeResolver.parseIp33Response(null));
        assertEquals("", AdcodeResolver.parseIp33Response("not json"));
    }

    @Test
    public void parseIp33ResponseDedupesAdjacentParts() {
        assertEquals(
                "北京市",
                AdcodeResolver.parseIp33Response(
                        "{\"province\":\"北京市\",\"city\":\"北京市\","
                                + "\"county\":\"北京市\"}"));
    }
}
