/*
 * Copyright (c) 2015, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.dataloader.action.visitor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.namespace.QName;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sforce.async.AsyncApiException;
import com.sforce.async.AsyncExceptionCode;
import com.sforce.async.AsyncXmlOutputStream;
import com.sforce.async.ContentType;
import com.sforce.async.JobInfo;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.MessageHandler;
import com.sforce.ws.MessageHandlerWithHeaders;
import com.sforce.ws.bind.CalendarCodec;
import com.sforce.ws.bind.TypeMapper;
import com.sforce.ws.parser.PullParserException;
import com.sforce.ws.parser.XmlInputStream;
import com.sforce.ws.parser.XmlOutputStream;
import com.sforce.ws.transport.Transport;
import com.sforce.ws.util.FileUtil;

public class BulkV2Connection  {
    private static final JsonFactory JSON_FACTORY = new JsonFactory(new ObjectMapper());
    private static final String URI_STEM_QUERY = "query/";
    private static final String AUTH_HEADER = "Authorization";
    private static final String AUTH_HEADER_VALUE_PREFIX = "Bearer ";
    public static final String NAMESPACE = "http://www.force.com/2009/06/asyncapi/dataload";
    public static final String SESSION_ID = "X-SFDC-Session";
    public static final String XML_CONTENT_TYPE = "application/xml";
    public static final String CSV_CONTENT_TYPE = "text/csv";
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String ZIP_XML_CONTENT_TYPE = "zip/xml";
    public static final String ZIP_CSV_CONTENT_TYPE = "zip/csv";
    public static final String ZIP_JSON_CONTENT_TYPE = "zip/json";
    public static final QName JOB_QNAME = new QName(NAMESPACE, "jobInfo");

    private String authHeaderValue = "";
    private String queryLocator = "";
    private int numberOfRecordsInQueryResult = 0;
    private ConnectorConfig config;
    private HashMap<String, String> headers = new HashMap<String, String>();
    public static final TypeMapper typeMapper = new TypeMapper(null, null, false);

    public BulkV2Connection(ConnectorConfig connectorConfig) throws AsyncApiException {
        this.config = connectorConfig;
    }
    
    public JobInfo createJob(JobInfo job) throws AsyncApiException {
        ContentType type = job.getContentType();
        if (type != null && type != ContentType.CSV) {
            throw new AsyncApiException("Unsupported Content Type", AsyncExceptionCode.FeatureNotEnabled);
        }
        return createOrUpdateJob(job, ContentType.CSV);
    }
    
    private JobInfo createOrUpdateJob(JobInfo job, ContentType contentType) throws AsyncApiException {
        String soqlStr = job.getObject();
        
        // hardcoded header for OAuth2 auth bearer token
        // TODO: figure a way to get OAuth2 auth bearer token from session id
        this.authHeaderValue = AUTH_HEADER_VALUE_PREFIX + getConfig().getSessionId();

        // Bulk v2 uses OAuth 2 access token for Auth

        try {
            Transport transport = getConfig().createTransport();
            OutputStream out;
            if (contentType == ContentType.XML || contentType == ContentType.ZIP_XML) {
                out = transport.connect(getConfig().getRestEndpoint() + URI_STEM_QUERY, getHeaders(XML_CONTENT_TYPE));
                XmlOutputStream xout = new AsyncXmlOutputStream(out, true);
                job.write(JOB_QNAME, xout, typeMapper);
                xout.close();
            } else {
                out = transport.connect(getConfig().getRestEndpoint() + URI_STEM_QUERY, getHeaders(JSON_CONTENT_TYPE));
                BulkV2QueryJobJSON queryObj = new BulkV2QueryJobJSON(soqlStr);
                serializeToJson (out, queryObj);
                out.close();
            }

            InputStream in = transport.getContent();

            if (transport.isSuccessful()) {
                if (contentType == ContentType.ZIP_XML || contentType == ContentType.XML) {
                    XmlInputStream xin = new XmlInputStream();
                    xin.setInput(in, "UTF-8");
                    JobInfo result = new JobInfo();
                    result.load(xin, typeMapper);
                    return result;
                } else {
                    JobInfo result = deserializeJsonToObject(in, JobInfo.class);
                    return result;
                }
            } else {
                parseAndThrowException(in, contentType);
            }
        } catch (PullParserException e) {
            throw new AsyncApiException("Failed to create job ", AsyncExceptionCode.ClientInputError, e);
        } catch (IOException e) {
            throw new AsyncApiException("Failed to create job ", AsyncExceptionCode.ClientInputError, e);
        } catch (ConnectionException e) {
            throw new AsyncApiException("Failed to create job ", AsyncExceptionCode.ClientInputError, e);
        }
        return null;
    }

    public void addHeader(String headerName, String headerValue) {
        headers.put(headerName, headerValue);
    }
    
    private ConnectorConfig getConfig() {
        return config;
    }
    
    static void parseAndThrowException(InputStream in, ContentType type) throws AsyncApiException {
        try {
            AsyncApiException exception;
            BulkV2QueryError[] errorList = deserializeJsonToObject(in, BulkV2QueryError[].class);
            if (errorList[0].message.contains("Aggregate Relationships not supported in Bulk Query")) {
                exception = new AsyncApiException(errorList[0].message, AsyncExceptionCode.FeatureNotEnabled);
            } else {
                exception = new AsyncApiException(errorList[0].errorCode + errorList[0].message, AsyncExceptionCode.Unknown);
            }
            throw exception;
        } catch (IOException e) {
            throw new AsyncApiException("Failed to parse exception", AsyncExceptionCode.ClientInputError, e);
        }
    }

    private HashMap<String, String> getHeaders(String contentType) {
        HashMap<String, String> newMap = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            newMap.put(entry.getKey(), entry.getValue());
        }
        newMap.put("Content-Type", contentType);
        newMap.put(AUTH_HEADER, this.authHeaderValue);
        return newMap;
    }
    
    static void serializeToJson(OutputStream out, Object value) throws IOException{
        JsonGenerator generator = JSON_FACTORY.createJsonGenerator(out);
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDateFormat(CalendarCodec.getDateFormat());
        mapper.writeValue(generator, value);
    }
    
    static <T> T deserializeJsonToObject (InputStream in, Class<T> tmpClass) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        // By default, ObjectMapper generates Calendar instances with UTC TimeZone.
        // Here, override that to "GMT" to better match the behavior of the WSC XML parser.
        mapper.setTimeZone(TimeZone.getTimeZone("GMT"));
        return mapper.readValue(in, tmpClass);
    }

    public JobInfo getJobStatus(String jobId) throws AsyncApiException {
        return getJobStatus(jobId, ContentType.JSON);
    }
    
    public JobInfo getJobStatus(String jobId, ContentType contentType) throws AsyncApiException {
        try {
            String endpoint = getConfig().getRestEndpoint();
            endpoint += URI_STEM_QUERY + jobId;
            URL url = new URL(endpoint);
    
            InputStream in = doGetJobStatusStream(url);
    
            if (contentType == ContentType.JSON  || contentType == ContentType.ZIP_JSON) {
                return deserializeJsonToObject(in, JobInfo.class);
            } else {
                JobInfo result = new JobInfo();
                XmlInputStream xin = new XmlInputStream();
                xin.setInput(in, "UTF-8");
                result.load(xin, typeMapper);
                return result;
            }
        } catch (PullParserException e) {
            throw new AsyncApiException("Failed to get job status ", AsyncExceptionCode.ClientInputError, e);
        } catch (IOException e) {
            throw new AsyncApiException("Failed to get job status ", AsyncExceptionCode.ClientInputError, e);
        } catch (ConnectionException e) {
            throw new AsyncApiException("Failed to get job status ", AsyncExceptionCode.ClientInputError, e);
        }
    }

    public InputStream getQueryResultStream(String jobId, String locator) throws AsyncApiException {
        String resultsURL = getConfig().getRestEndpoint() + URI_STEM_QUERY + jobId + "/results";
        if (locator != null && !locator.isEmpty() && !"null".equalsIgnoreCase(locator)) {
            resultsURL += "?locator=" + locator;
        }
        try {
            return doGetQueryResultStream(new URL(resultsURL));
        } catch (IOException e) {
            throw new AsyncApiException("Failed to get result ", AsyncExceptionCode.ClientInputError, e);
        }
    }
    
    private InputStream doGetQueryResultStream(URL resultsURL) throws IOException, AsyncApiException {
        HttpURLConnection httpConnection = openHttpConnection(resultsURL);
        InputStream is = doHttpGet(httpConnection, resultsURL);
        this.queryLocator = httpConnection.getHeaderField("Sforce-Locator");
        this.numberOfRecordsInQueryResult = Integer.valueOf(httpConnection.getHeaderField("Sforce-NumberOfRecords"));
        return is;
    }
    
    private InputStream doGetJobStatusStream(URL jobStatusURL) throws IOException, AsyncApiException {
        HttpURLConnection httpConnection = openHttpConnection(jobStatusURL);
        return doHttpGet(httpConnection, jobStatusURL);
    }
    
    private HttpURLConnection openHttpConnection(URL url) throws IOException {
        HttpURLConnection connection = getConfig().createConnection(url, null);
        SSLContext sslContext = getConfig().getSslContext();
        if (sslContext != null && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection)connection).setSSLSocketFactory(sslContext.getSocketFactory());
        }
        connection.setRequestProperty(AUTH_HEADER, this.authHeaderValue);
        return connection;
    }
    
    private InputStream doHttpGet(HttpURLConnection connection, URL url) throws IOException, AsyncApiException {
        boolean success = true;
        InputStream in;
        try {
            in = connection.getInputStream();
        } catch (IOException e) {
            success = false;
            in = connection.getErrorStream();
        }

        String encoding = connection.getHeaderField("Content-Encoding");
        if ("gzip".equals(encoding)) {
            in = new GZIPInputStream(in);
        }

        if (getConfig().isTraceMessage() || getConfig().hasMessageHandlers()) {
            byte[] bytes = FileUtil.toBytes(in);
            in = new ByteArrayInputStream(bytes);

            if (getConfig().hasMessageHandlers()) {
                Iterator<MessageHandler> it = getConfig().getMessagerHandlers();
                while (it.hasNext()) {
                    MessageHandler handler = it.next();
                    if (handler instanceof MessageHandlerWithHeaders) {
                        ((MessageHandlerWithHeaders)handler).handleRequest(url, new byte[0], null);
                        ((MessageHandlerWithHeaders)handler).handleResponse(url, bytes, connection.getHeaderFields());
                    } else {
                        handler.handleRequest(url, new byte[0]);
                        handler.handleResponse(url, bytes);
                    }
                }
            }

            if (getConfig().isTraceMessage()) {
                getConfig().getTraceStream().println(url.toExternalForm());

                Map<String, List<String>> headers = connection.getHeaderFields();
                for (Map.Entry<String, List<String>>entry : headers.entrySet()) {
                    StringBuffer sb = new StringBuffer();
                    List<String> values = entry.getValue();

                    if (values != null) {
                        for (String v : values) {
                            sb.append(v);
                        }
                    }

                    getConfig().getTraceStream().println(entry.getKey() + ": " + sb.toString());
                }

                getConfig().teeInputStream(bytes);
            }
        }

        if (!success) {
            ContentType type = null;
            String contentTypeHeader = connection.getContentType();
            if (contentTypeHeader != null) {
                if (contentTypeHeader.contains(XML_CONTENT_TYPE)) {
                    type = ContentType.XML;
                } else if (contentTypeHeader.contains(JSON_CONTENT_TYPE)) {
                    type = ContentType.JSON;
                }
            }
            parseAndThrowException(in, type);
        }
        return in;
    }
    
    public String getQueryLocator() {
        return this.queryLocator;
    }
    
    public int getNumberOfRecordsInQueryResult() {
        return this.numberOfRecordsInQueryResult;
    }
}

/*
 * represent body of the request. Example:
 * {
 *   "operation": "query",
 *   "query": "SELECT Id FROM Account"
 * }
 */
class BulkV2QueryJobJSON implements Serializable {
    private static final long serialVersionUID = 2L;
    String operation = "query";
    String query = "";
    
    BulkV2QueryJobJSON(String query) {
        this.query = query;
    }
    
    public void setOperation(String operation) {
        this.operation = operation;
    }
    
    public String getOperation() {
        return this.operation;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public String getQuery() {
        return this.query;
    }
}

class BulkV2QueryError implements Serializable {
    private static final long serialVersionUID = 3L;
    public String errorCode = "";
    public String message = "";
}