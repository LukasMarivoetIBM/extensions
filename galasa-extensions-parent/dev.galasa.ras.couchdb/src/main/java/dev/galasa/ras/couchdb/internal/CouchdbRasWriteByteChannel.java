/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2019.
 */
package dev.galasa.ras.couchdb.internal;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.util.EntityUtils;

import dev.galasa.ResultArchiveStoreContentType;
import dev.galasa.ras.couchdb.internal.pojos.PutPostResponse;

/**
 * Dummy Byte Channel for a null Result Archive Store
 *
 * @author Michael Baylis
 *
 */
public class CouchdbRasWriteByteChannel implements SeekableByteChannel {

    private final static Log                    logger = LogFactory.getLog(CouchdbRasWriteByteChannel.class);

    private static final Charset                UTF8   = Charset.forName("utf-8");

    private final Path                          cachePath;
    private final SeekableByteChannel           cacheByteChannel;

    private final Path                          remotePath;
    private final ResultArchiveStoreContentType remoteContentType;
    private final CouchdbRasStore               couchdbRasStore;

    CouchdbRasWriteByteChannel(CouchdbRasStore couchdbRasStore, Path remotePath,
            ResultArchiveStoreContentType remoteContentType, Set<? extends OpenOption> options,
            FileAttribute<?>[] attrs) throws IOException {
        this.couchdbRasStore = couchdbRasStore;
        this.remotePath = remotePath;

        if (remoteContentType != null) {
            this.remoteContentType = remoteContentType;
        } else {
            this.remoteContentType = ResultArchiveStoreContentType.TEXT;
        }

        // TODO put these all in the same /tmp/galasa dir so it is easy to cleanup
        cachePath = Files.createTempFile("galasa_couchdb", "temp");

        // TODO are we going to provide other options?
        // StandardOpenOption.TRUNCATE_EXISTING need to standardise across the board
        cacheByteChannel = Files.newByteChannel(cachePath, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.nio.channels.Channel#isOpen()
     */
    @Override
    public boolean isOpen() {
        return cacheByteChannel.isOpen();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.nio.channels.Channel#close()
     */
    @Override
    public void close() throws IOException {
        cacheByteChannel.close();

        String encodedRemotePath = URLEncoder.encode(this.remotePath.toString(), UTF8.name());

        HttpPut request = new HttpPut(this.couchdbRasStore.getCouchdbUri() + "/galasa_artifacts/"
                + this.couchdbRasStore.getArtifactDocumentId() + "/" + encodedRemotePath);
        request.setEntity(new FileEntity(cachePath.toFile()));
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", remoteContentType.value());
        request.addHeader("If-Match", this.couchdbRasStore.getArtifactDocumentRev());

        try (CloseableHttpResponse response = this.couchdbRasStore.getHttpClient().execute(request)) {
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
                if (statusLine.getStatusCode() == HttpStatus.SC_CONFLICT) {
                    logger.error(
                            "The run document has been updated by another engine, terminating now to avoid corruption");
                    System.exit(0);
                }
                throw new IOException("Unable to store the artifact attachment - " + statusLine.toString());
            }
            HttpEntity entity = response.getEntity();
            PutPostResponse putPostResponse = this.couchdbRasStore.getGson().fromJson(EntityUtils.toString(entity),
                    PutPostResponse.class);
            if (putPostResponse.id == null || putPostResponse.rev == null) {
                throw new CouchdbRasException("Unable to store the test structure - Invalid JSON response");
            }
            this.couchdbRasStore.updateArtifactDocumentRev(putPostResponse.rev);

            logger.info("Stored artifact " + this.remotePath + ", length=" + Files.size(cachePath) + ", contentType="
                    + this.remoteContentType.value());
        } catch (Exception e) {
            throw new IOException("Unable to store artifact attachment", e);
        } finally {
            try {
                Files.delete(cachePath);
            } catch (Exception e) {
            } // *** Hide any delete problems
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.nio.channels.SeekableByteChannel#read(java.nio.ByteBuffer)
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        return cacheByteChannel.read(dst);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.nio.channels.SeekableByteChannel#write(java.nio.ByteBuffer)
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        return cacheByteChannel.write(src);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.nio.channels.SeekableByteChannel#position()
     */
    @Override
    public long position() throws IOException {
        return cacheByteChannel.position();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.nio.channels.SeekableByteChannel#position(long)
     */
    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        return cacheByteChannel.position(newPosition);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.nio.channels.SeekableByteChannel#size()
     */
    @Override
    public long size() throws IOException {
        return cacheByteChannel.size();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.nio.channels.SeekableByteChannel#truncate(long)
     */
    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        return cacheByteChannel.truncate(size);
    }

}
