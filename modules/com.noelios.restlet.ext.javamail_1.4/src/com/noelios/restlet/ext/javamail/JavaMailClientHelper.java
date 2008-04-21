/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet.ext.javamail;

import java.io.IOException;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;

import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.UIDFolder;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.restlet.Client;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.Representation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.noelios.restlet.ClientHelper;
import com.sun.mail.pop3.POP3Folder;

/**
 * Client connector to a mail server. Currently only the SMTP protocol is
 * supported. To send an email, specify a SMTP URI as the ressource reference of
 * the call and use an XML email as the content of the call. An SMTP URI has the
 * following syntax: smtp://host[:port]<br>
 * <br>
 * The default port used is 25 for SMTP and 465 for SMTPS. Use the
 * Call.getSecurity().setLogin() and setPassword() methods for authentication.<br>
 * <br>
 * Sample XML email:<br>
 * {@code <?xml version="1.0" encoding="ISO-8859-1" ?>}<br>
 * {@code <email>}<br>
 * &nbsp;&nbsp;{@code   <head>}<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;{@code      <subject>Account activation</subject>}<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;{@code      <from>sender@company.com</from>}<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;{@code      <to>user@domain.com</to>}<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;{@code      <cc>log@restlet.org</cc>}<br>
 * &nbsp;&nbsp;{@code   </head>}<br>
 * &nbsp;&nbsp;{@code   <body><![CDATA[Your account was sucessfully created!]]></body>}<br>
 * {@code </email>}
 * 
 * Here is the list of parameters that are supported: <table>
 * <tr>
 * <th>Parameter name</th>
 * <th>Value type</th>
 * <th>Default value</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>startTls</td>
 * <td>boolean</td>
 * <td>false</td>
 * <td>If true, the SMTP connector will attempt to start a TLS tunnel, right
 * after the SMTP connection is established.</td>
 * </tr>
 * <tr>
 * <td>debug</td>
 * <td>boolean</td>
 * <td>false</td>
 * <td>If true, the connector will generate JavaMail debug messages.</td>
 * </tr>
 * </table>
 * 
 * 
 * @author Jerome Louvel (contact@noelios.com)
 */
public class JavaMailClientHelper extends ClientHelper {
    /**
     * Creates a high-level request.
     * 
     * @param smtpURI
     *                The SMTP server's URI (ex: smtp://localhost).
     * @param email
     *                The email to send (valid XML email).
     * @deprecated With no replacement as it creates an unecessary dependency on
     *             NRE classes.
     */
    @Deprecated
    public static Request create(String smtpURI, Representation email) {
        Request result = new Request();
        result.setMethod(Method.POST);
        result.setResourceRef(smtpURI);
        result.setEntity(email);
        return result;
    }

    /**
     * Creates a high-level request.
     * 
     * @param smtpURI
     *                The SMTP server's URI (ex: smtp://localhost).
     * @param email
     *                The email to send (valid XML email).
     * @param login
     *                Authenticate using this login name.
     * @param password
     *                Authenticate using this password.
     * @deprecated With no replacement as it creates an unecessary dependency on
     *             NRE classes.
     */
    @Deprecated
    public static Request create(String smtpURI, Representation email,
            String login, String password) {
        Request result = create(smtpURI, email);
        result.getChallengeResponse().setIdentifier(login);
        result.getChallengeResponse().setSecret(password);
        return result;
    }

    /**
     * Constructor.
     * 
     * @param client
     *                The client to help.
     */
    public JavaMailClientHelper(Client client) {
        super(client);
        getProtocols().add(Protocol.SMTP);
        getProtocols().add(Protocol.SMTPS);
        getProtocols().add(Protocol.POP);
        getProtocols().add(Protocol.POPS);
    }

    /**
     * Returns the request login.
     * 
     * @param request
     *                The high-level request.
     * @return The login.
     */
    private String getLogin(Request request) {
        return request.getChallengeResponse().getIdentifier();
    }

    /**
     * Returns the request password.
     * 
     * @param request
     *                The high-level request.
     * @return The password.
     */
    private String getPassword(Request request) {
        return new String(request.getChallengeResponse().getSecret());
    }

    @Override
    public void handle(Request request, Response response) {
        try {
            Protocol protocol = request.getProtocol();

            if (Protocol.SMTP.equals(protocol)
                    || Protocol.SMTPS.equals(protocol)) {
                handleSmtp(request, response);
            } else if (Protocol.POP.equals(protocol)
                    || Protocol.POPS.equals(protocol)) {
                handlePop(request, response);
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "JavaMail client error", e);
            response.setStatus(Status.CONNECTOR_ERROR_INTERNAL, e);
        } catch (NoSuchProviderException e) {
            getLogger().log(Level.WARNING, "JavaMail client error", e);
            response.setStatus(Status.SERVER_ERROR_INTERNAL, e);
        } catch (AddressException e) {
            getLogger().log(Level.WARNING, "JavaMail client error", e);
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, e);
        } catch (MessagingException e) {
            getLogger().log(Level.WARNING, "JavaMail client error", e);
            response.setStatus(Status.SERVER_ERROR_INTERNAL, e);
        }
    }

    /**
     * Handles a POP or POPS request.
     * 
     * @param request
     *                The request to handle.
     * @param response
     *                The response to update.
     * @throws IOException
     * @throws MessagingException
     */
    private void handlePop(Request request, Response response)
            throws MessagingException {
        // Parse the POP URI
        String popHost = request.getResourceRef().getHostDomain();
        int popPort = request.getResourceRef().getHostPort();

        if (popPort == -1) {
            // No port specified, the default one should be used
            popPort = request.getProtocol().getDefaultPort();
        }

        if ((popHost == null) || (popHost.equals(""))) {
            throw new IllegalArgumentException("Invalid POP host specified");
        }

        // Check if authentication required
        boolean authenticate = ((getLogin(request) != null) && (getPassword(request) != null));
        boolean apop = authenticate
                && (ChallengeScheme.POP_DIGEST.equals(request
                        .getChallengeResponse().getScheme()));

        String transport = null;

        if (Protocol.POP.equals(request.getProtocol())) {
            transport = "pop3";
        } else if (Protocol.POPS.equals(request.getProtocol())) {
            transport = "pop3s";
        }

        Properties props = System.getProperties();
        props.put("mail." + transport + ".host", popHost);
        props.put("mail." + transport + ".port", Integer.toString(popPort));
        props.put("mail." + transport + ".apop.enable", Boolean.toString(apop));

        Session session = Session.getDefaultInstance(props);
        session.setDebug(isDebug());
        Store store = session.getStore(transport);
        store.connect(getLogin(request), getPassword(request));
        POP3Folder inbox = (POP3Folder) store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);
        FetchProfile profile = new FetchProfile();
        profile.add(UIDFolder.FetchProfileItem.UID);
        Message[] messages = inbox.getMessages();
        inbox.fetch(messages, profile);

        for (int i = 0; i < messages.length; i++) {
            String uid = inbox.getUID(messages[i]);
            System.out.println("UID: " + uid);
        }
    }

    /**
     * Handles a SMTP or SMTPS request.
     * 
     * @param request
     *                The request to handle.
     * @param response
     *                The response to update.
     * @throws IOException
     * @throws MessagingException
     */
    private void handleSmtp(Request request, Response response)
            throws IOException, MessagingException {
        // Parse the SMTP URI
        String smtpHost = request.getResourceRef().getHostDomain();
        int smtpPort = request.getResourceRef().getHostPort();

        if (smtpPort == -1) {
            // No port specified, the default one should be used
            smtpPort = request.getProtocol().getDefaultPort();
        }

        if ((smtpHost == null) || (smtpHost.equals(""))) {
            throw new IllegalArgumentException("Invalid SMTP host specified");
        }

        // Parse the email to extract necessary info
        DomRepresentation dom = new DomRepresentation(request.getEntity());
        Document email = dom.getDocument();
        Element root = (Element) email.getElementsByTagName("email").item(0);
        Element header = (Element) root.getElementsByTagName("head").item(0);
        String subject = header.getElementsByTagName("subject").item(0)
                .getTextContent();
        String from = header.getElementsByTagName("from").item(0)
                .getTextContent();

        NodeList toList = header.getElementsByTagName("to");
        String[] to = new String[toList.getLength()];
        for (int i = 0; i < toList.getLength(); i++) {
            to[i] = toList.item(i).getTextContent();
        }

        NodeList ccList = header.getElementsByTagName("cc");
        String[] cc = new String[ccList.getLength()];
        for (int i = 0; i < ccList.getLength(); i++) {
            cc[i] = ccList.item(i).getTextContent();
        }

        NodeList bccList = header.getElementsByTagName("bcc");
        String[] bcc = new String[bccList.getLength()];
        for (int i = 0; i < bccList.getLength(); i++) {
            bcc[i] = bccList.item(i).getTextContent();
        }

        String text = root.getElementsByTagName("body").item(0)
                .getTextContent();

        // Check if authentication required
        boolean authenticate = ((getLogin(request) != null) && (getPassword(request) != null));

        String transport = null;

        if (Protocol.SMTP.equals(request.getProtocol())) {
            transport = "smtp";
        } else if (Protocol.SMTPS.equals(request.getProtocol())) {
            transport = "smtps";
        }

        Properties props = System.getProperties();
        props.put("mail." + transport + ".host", smtpHost);
        props.put("mail." + transport + ".port", Integer.toString(smtpPort));
        props.put("mail." + transport + ".auth", Boolean.toString(authenticate)
                .toLowerCase());
        props.put("mail." + transport + ".starttls.enable", Boolean
                .toString(isStartTls()));

        // Open the JavaMail session
        Session session = Session.getDefaultInstance(props);
        session.setDebug(isDebug());
        Transport tr = session.getTransport(transport);

        if (tr != null) {
            // Check if authentication is needed
            if (authenticate) {
                tr.connect(smtpHost, getLogin(request), getPassword(request));
            } else {
                tr.connect();
            }

            // Actually send the message
            if (tr.isConnected()) {
                getLogger()
                        .info(
                                "JavaMail client connection successfully established. Attempting to send the message");

                // Create a new message
                Message msg = new MimeMessage(session);

                // Set the FROM and TO fields
                msg.setFrom(new InternetAddress(from));

                for (String element : to) {
                    msg.addRecipient(Message.RecipientType.TO,
                            new InternetAddress(element));
                }

                for (String element : cc) {
                    msg.addRecipient(Message.RecipientType.CC,
                            new InternetAddress(element));
                }

                for (String element : bcc) {
                    msg.addRecipient(Message.RecipientType.BCC,
                            new InternetAddress(element));
                }

                // Set the subject and content text
                msg.setSubject(subject);
                msg.setText(text);
                msg.setSentDate(new Date());
                msg.saveChanges();

                // Send the message
                tr.sendMessage(msg, msg.getAllRecipients());
                tr.close();

                getLogger().info(
                        "JavaMail client successfully sent the message.");
            }
        }
    }

    /**
     * Indicates if the connector should generate JavaMail debug messages.
     * 
     * @return True the connector should generate JavaMail debug messages.
     */
    public boolean isDebug() {
        return Boolean.parseBoolean(getParameters().getFirstValue("debug",
                "false"));
    }

    /**
     * Indicates if the SMTP protocol should attempt to start a TLS tunnel.
     * 
     * @return True if the SMTP protocol should attempt to start a TLS tunnel.
     */
    public boolean isStartTls() {
        return Boolean.parseBoolean(getParameters().getFirstValue("startTls",
                "false"));
    }

}
