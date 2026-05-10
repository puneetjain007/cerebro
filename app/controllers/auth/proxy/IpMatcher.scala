package controllers.auth.proxy

import java.net.InetAddress

object IpMatcher {

  // Matches an IP address against either an exact IP or a CIDR block (e.g. "10.0.0.0/8").
  def matches(ip: String, cidr: String): Boolean =
    if (cidr.contains("/")) matchesCidr(ip, cidr)
    else ip == cidr

  private def matchesCidr(ip: String, cidr: String): Boolean =
    scala.util.Try {
      val Array(networkStr, prefixLenStr) = cidr.split("/", 2)
      val prefixLen = prefixLenStr.toInt
      val ipBytes      = InetAddress.getByName(ip).getAddress
      val networkBytes = InetAddress.getByName(networkStr).getAddress
      if (ipBytes.length != networkBytes.length) return false
      val fullBytes = prefixLen / 8
      val remainder = prefixLen % 8
      // Compare full bytes
      (0 until fullBytes).forall(i => ipBytes(i) == networkBytes(i)) && {
        // Compare partial byte (if any)
        if (remainder == 0) true
        else {
          val mask = (0xFF << (8 - remainder)) & 0xFF
          (ipBytes(fullBytes) & mask) == (networkBytes(fullBytes) & mask)
        }
      }
    }.getOrElse(false)

}
