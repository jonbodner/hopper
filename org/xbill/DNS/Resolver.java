import java.util.*;
import java.io.*;
import java.net.*;

public class dnsResolver {

InetAddress addr;
int port = dns.PORT;
byte [] TSIGKey = null;

public dnsResolver(String hostname) {
	try {
		addr = InetAddress.getByName(hostname);
	}
	catch (UnknownHostException e) {
		System.out.println("Unknown host " + hostname);
		return;
	}
}

public void setPort(int port) {
	this.port = port;
}

public void setTSIGKey(byte [] key) {
	TSIGKey = key;
}

private byte [] toBytes(dnsMessage m) throws IOException {
	ByteArrayOutputStream os;

	os = new ByteArrayOutputStream();
	m.toBytes(new DataOutputStream(os));
	return os.toByteArray();
}

private byte [] toCanonicalBytes(dnsMessage m) throws IOException {
	ByteArrayOutputStream os;

	os = new ByteArrayOutputStream();
	m.toCanonicalBytes(new DataOutputStream(os));
	return os.toByteArray();
}

private dnsMessage parse(byte [] in) throws IOException {
	ByteArrayInputStream is;

	is = new ByteArrayInputStream(in);
	return new dnsMessage(new CountedDataInputStream(is));
}


dnsMessage sendTCP(byte [] out) throws IOException {
	byte [] in;
	Socket s;
	int inLength;
	DataInputStream dataIn;

	try {
		s = new Socket(addr, port);
	}
	catch (SocketException e) {
		System.out.println(e);
		return null;
	}

	new DataOutputStream(s.getOutputStream()).writeShort(out.length);
	s.getOutputStream().write(out);

	dataIn = new DataInputStream(s.getInputStream());
	inLength = dataIn.readUnsignedShort();
	in = new byte[inLength];
	dataIn.readFully(in);

	s.close();
	return parse(in);
}

public dnsMessage send(dnsMessage query) throws IOException {
	byte [] out, in;
	dnsMessage response;
	DatagramSocket s;

	try {
		s = new DatagramSocket();
	}
	catch (SocketException e) {
		System.out.println(e);
		return null;
	}

	if (TSIGKey != null) {
		hmacSigner h = new hmacSigner(TSIGKey);
		h.addData(toCanonicalBytes(query));
		try {
			String local = InetAddress.getLocalHost().getHostName();
			dnsRecord r;
			r = new dnsTSIGRecord(new dnsName(local), dns.IN, 0,
					      new dnsName(dns.HMAC), new Date(),
					      (short)300, h.sign(),
					      dns.NOERROR, null);
			query.addRecord(dns.ADDITIONAL, r);
			System.out.println(r);
		}
		catch (UnknownHostException e) {
		}
	}

	out = toBytes(query);
	s.send(new DatagramPacket(out, out.length, addr, port));

	in = new byte[512];
	s.receive(new DatagramPacket(in, in.length));
	response = parse(in);
	s.close();
	if (response.getHeader().getFlag(dns.TC))
		return sendTCP(out);
	else
		return response;
}

public dnsMessage sendAXFR(dnsMessage inMessage) throws IOException {
	byte [] out, in;
	Socket s;
	int inLength;
	DataInputStream dataIn;
	int soacount = 0;
	dnsMessage outMessage;

	try {
		s = new Socket(addr, dns.PORT);
	}
	catch (SocketException e) {
		System.out.println(e);
		return null;
	}

	out = toBytes(inMessage);
	new DataOutputStream(s.getOutputStream()).writeShort(out.length);
	s.getOutputStream().write(out);

	outMessage = new dnsMessage();
	outMessage.getHeader().setID(inMessage.getHeader().getID());
	while (true) {
		dataIn = new DataInputStream(s.getInputStream());
		inLength = dataIn.readUnsignedShort();
		in = new byte[inLength];
		dataIn.readFully(in);
		dnsMessage m = parse(in);
		if (m.getHeader().getCount(dns.QUESTION) != 0 ||
		    m.getHeader().getCount(dns.ANSWER) <= 0 ||
		    m.getHeader().getCount(dns.AUTHORITY) != 0 ||
		    m.getHeader().getCount(dns.ADDITIONAL) != 0)
			throw new IOException("Invalid AXFR message");
		Vector v = m.getSection(dns.ANSWER);
		Enumeration e = v.elements();
		while (e.hasMoreElements()) {
			dnsRecord r = (dnsRecord)e.nextElement();
			outMessage.addRecord(dns.ANSWER, r);
			if (r instanceof dnsSOARecord)
				soacount++;
		}
		if (soacount > 1)
			break;
	}

	s.close();
	return outMessage;
}


}
