package biz.neustar.hopper.nio;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.junit.Test;

import biz.neustar.hopper.exception.TextParseException;
import biz.neustar.hopper.nio.example.EchoServerHandler;

/**
 * Test for the UDP client
 * 
 * @author Marty Kube <marty@beavercreekconsulting.com>
 * 
 */
public class UDPClientTest {

	@Test
	public void test() throws TextParseException, UnknownHostException, InterruptedException {

		Server server = Server.builder().port(0).serverMessageHandler(new EchoServerHandler()).build();
		MessageReceivedTrap messageReceivedTrap = new MessageReceivedTrap(1);
		Client client = Client.builder().clientMessageHandler(messageReceivedTrap).build();
		client.sendUDP(TCPClientTest.getQuery(0), new InetSocketAddress("localhost", server.getLocalAddress().getPort()));
		try {
			Assert.assertTrue(messageReceivedTrap.latch.await(2, TimeUnit.SECONDS));
		} finally {
			server.stop();
		}
	}

}
