/**
 * Copyright © 2016-2017 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.transport.mqtt;

import com.google.common.io.Resources;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.dao.EncryptionUtil;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.transport.mqtt.util.SslUtil;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Created by valerii.sosliuk on 11/6/16.
 */
@Slf4j
@Component("MqttSslHandlerProvider")
@ConditionalOnProperty(prefix = "mqtt.ssl", value = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttSslHandlerProvider {

    @Value("${mqtt.ssl.protocol}")
    private String sslProtocol;
    @Value("${mqtt.ssl.key_store}")
    private String keyStoreFile;
    @Value("${mqtt.ssl.key_store_password}")
    private String keyStorePassword;
    @Value("${mqtt.ssl.key_password}")
    private String keyPassword;
    @Value("${mqtt.ssl.key_store_type}")
    private String keyStoreType;

    @Autowired
    private DeviceCredentialsService deviceCredentialsService;


    public SslHandler getSslHandler() {
        try {
            URL ksUrl = Resources.getResource(keyStoreFile);
            File ksFile = new File(ksUrl.toURI());
            URL tsUrl = Resources.getResource(keyStoreFile);
            File tsFile = new File(tsUrl.toURI());

            TrustManagerFactory tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore trustStore = KeyStore.getInstance(keyStoreType);
            try (InputStream tsFileInputStream = new FileInputStream(tsFile)) {
                trustStore.load(tsFileInputStream, keyStorePassword.toCharArray());
            }
            tmFactory.init(trustStore);

            KeyStore ks = KeyStore.getInstance(keyStoreType);
            try (InputStream ksFileInputStream = new FileInputStream(ksFile)) {
                ks.load(ksFileInputStream, keyStorePassword.toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyPassword.toCharArray());

            KeyManager[] km = kmf.getKeyManagers();
            TrustManager x509wrapped = getX509TrustManager(tmFactory);
            TrustManager[] tm = {x509wrapped};
            if (StringUtils.isEmpty(sslProtocol)) {
                sslProtocol = "TLS";
            }

            String ciphersToBeEnabled[] ={};
	    ciphersToBeEnabled[0] = "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA";
	    ciphersToBeEnabled[1] = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA";                                        
	    ciphersToBeEnabled[2] = "TLS_RSA_WITH_AES_256_CBC_SHA";                                            
	    ciphersToBeEnabled[3] = "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA";                                           
	    ciphersToBeEnabled[4] = "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA";                                             
	    ciphersToBeEnabled[5] = "TLS_DHE_RSA_WITH_AES_256_CBC_SHA";                                              
	    ciphersToBeEnabled[6] = "TLS_DHE_DSS_WITH_AES_256_CBC_SHA";                                              
	    ciphersToBeEnabled[7] = "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA";                                          
	    ciphersToBeEnabled[8] = "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA";                                            
	    ciphersToBeEnabled[9] = "TLS_RSA_WITH_AES_128_CBC_SHA";                                                  
	    ciphersToBeEnabled[10] = "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA";                                           
	    ciphersToBeEnabled[11] = "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA";                                             
	    ciphersToBeEnabled[12] = "TLS_DHE_RSA_WITH_AES_128_CBC_SHA";                                              
	    ciphersToBeEnabled[13] = "TLS_DHE_DSS_WITH_AES_128_CBC_SHA";                                              
	    ciphersToBeEnabled[14] = "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA";                                         
	    ciphersToBeEnabled[15] = "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA";                                           
	    ciphersToBeEnabled[16] = "SSL_RSA_WITH_3DES_EDE_CBC_SHA";                                                 
	    ciphersToBeEnabled[17] = "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA";                                          
	    ciphersToBeEnabled[18] = "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA"; 

            SSLContext sslContext = SSLContext.getInstance(sslProtocol);
            sslContext.init(km, tm, null);
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            sslEngine.setNeedClientAuth(false);
            sslEngine.setWantClientAuth(true);
            sslEngine.setEnabledProtocols(sslEngine.getSupportedProtocols());
            sslEngine.setEnabledCipherSuites(ciphersToBeEnabled);
            sslEngine.setEnableSessionCreation(true);
            return new SslHandler(sslEngine);
        } catch (Exception e) {
            log.error("Unable to set up SSL context. Reason: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get SSL handler", e);
        }
    }

    private TrustManager getX509TrustManager(TrustManagerFactory tmf) throws Exception {
        X509TrustManager x509Tm = null;
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                x509Tm = (X509TrustManager) tm;
                break;
            }
        }
        return new ThingsboardMqttX509TrustManager(x509Tm, deviceCredentialsService);
    }

    static class ThingsboardMqttX509TrustManager implements X509TrustManager {

        private final X509TrustManager trustManager;
        private DeviceCredentialsService deviceCredentialsService;

        ThingsboardMqttX509TrustManager(X509TrustManager trustManager, DeviceCredentialsService deviceCredentialsService) {
            this.trustManager = trustManager;
            this.deviceCredentialsService = deviceCredentialsService;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return trustManager.getAcceptedIssuers();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
            trustManager.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
            DeviceCredentials deviceCredentials = null;
            for (X509Certificate cert : chain) {
                try {
                    String strCert = SslUtil.getX509CertificateString(cert);
                    String sha3Hash = EncryptionUtil.getSha3Hash(strCert);
                    deviceCredentials = deviceCredentialsService.findDeviceCredentialsByCredentialsId(sha3Hash);
                    if (deviceCredentials != null && strCert.equals(deviceCredentials.getCredentialsValue())) {
                        break;
                    }
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
            if (deviceCredentials == null) {
                throw new CertificateException("Invalid Device Certificate");
            }
        }
    }
}
