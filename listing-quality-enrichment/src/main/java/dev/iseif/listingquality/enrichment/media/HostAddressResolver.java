package dev.iseif.listingquality.enrichment.media;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@FunctionalInterface
interface HostAddressResolver {

  List<InetAddress> resolve(String host) throws UnknownHostException;
}
