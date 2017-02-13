/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.xml.security.encryption;

import java.io.IOException;
import java.util.Base64;

import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.utils.resolver.ResourceResolver;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.transforms.TransformationException;
import org.w3c.dom.Attr;

/**
 * <code>XMLCipherInput</code> is used to wrap input passed into the
 * XMLCipher encryption operations.
 *
 * In decryption mode, it takes a <code>CipherData</code> object and allows
 * callers to dereference the CipherData into the encrypted bytes that it
 * actually represents.  This takes care of all base64 encoding etc.
 *
 * While primarily an internal class, this can be used by applications to
 * quickly and easily retrieve the encrypted bytes from an EncryptedType
 * object
 *
 * @author Berin Lautenbach
 */
public class XMLCipherInput {

    private static org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(XMLCipherInput.class);

    /** The data we are working with */
    private CipherData cipherData;

    /** MODES */
    private int mode;

    private boolean secureValidation;

    /**
     * Constructor for processing encrypted octets
     *
     * @param data The <code>CipherData</code> object to read the bytes from
     * @throws XMLEncryptionException {@link XMLEncryptionException}
     */
    public XMLCipherInput(CipherData data) throws XMLEncryptionException {
        cipherData = data;
        mode = XMLCipher.DECRYPT_MODE;
        if (cipherData == null) {
            throw new XMLEncryptionException("CipherData is null");
        }
    }

    /**
     * Constructor for processing encrypted octets
     *
     * @param input The <code>EncryptedType</code> object to read
     * the bytes from.
     * @throws XMLEncryptionException {@link XMLEncryptionException}
     */
    public XMLCipherInput(EncryptedType input) throws XMLEncryptionException {
        cipherData = input == null ? null : input.getCipherData();
        mode = XMLCipher.DECRYPT_MODE;
        if (cipherData == null) {
            throw new XMLEncryptionException("CipherData is null");
        }
    }

    /**
     * Set whether secure validation is enabled or not. The default is false.
     */
    public void setSecureValidation(boolean secureValidation) {
        this.secureValidation = secureValidation;
    }

    /**
     * Dereferences the input and returns it as a single byte array.
     *
     * @throws XMLEncryptionException
     * @return The decripted bytes.
     */
    public byte[] getBytes() throws XMLEncryptionException {
        if (mode == XMLCipher.DECRYPT_MODE) {
            return getDecryptBytes();
        }
        return null;
    }

    /**
     * Internal method to get bytes in decryption mode
     * @return the decrypted bytes
     * @throws XMLEncryptionException
     */
    private byte[] getDecryptBytes() throws XMLEncryptionException {
        String base64EncodedEncryptedOctets = null;

        if (cipherData.getDataType() == CipherData.REFERENCE_TYPE) {
            // Fun time!
            if (log.isDebugEnabled()) {
                LOG.debug("Found a reference type CipherData");
            }
            CipherReference cr = cipherData.getCipherReference();

            // Need to wrap the uri in an Attribute node so that we can
            // Pass to the resource resolvers

            Attr uriAttr = cr.getURIAsAttr();
            XMLSignatureInput input = null;

            try {
                ResourceResolver resolver =
                    ResourceResolver.getInstance(uriAttr, null, secureValidation);
                input = resolver.resolve(uriAttr, null, secureValidation);
            } catch (ResourceResolverException ex) {
                throw new XMLEncryptionException(ex);
            }

            if (input != null) {
                if (log.isDebugEnabled()) {
                    LOG.debug("Managed to resolve URI \"" + cr.getURI() + "\"");
                }
            } else {
                if (log.isDebugEnabled()) {
                    LOG.debug("Failed to resolve URI \"" + cr.getURI() + "\"");
                }
            }

            // Lets see if there are any transforms
            Transforms transforms = cr.getTransforms();
            if (transforms != null) {
                if (log.isDebugEnabled()) {
                    LOG.debug("Have transforms in cipher reference");
                }
                try {
                    org.apache.xml.security.transforms.Transforms dsTransforms =
                        transforms.getDSTransforms();
                    dsTransforms.setSecureValidation(secureValidation);
                    input = dsTransforms.performTransforms(input);
                } catch (TransformationException ex) {
                    throw new XMLEncryptionException(ex);
                }
            }

            try {
                return input.getBytes();
            } catch (IOException ex) {
                throw new XMLEncryptionException(ex);
            } catch (CanonicalizationException ex) {
                throw new XMLEncryptionException(ex);
            }

            // retrieve the cipher text
        } else if (cipherData.getDataType() == CipherData.VALUE_TYPE) {
            base64EncodedEncryptedOctets = cipherData.getCipherValue().getValue();
        } else {
            throw new XMLEncryptionException("CipherData.getDataType() returned unexpected value");
        }

        if (log.isDebugEnabled()) {
            LOG.debug("Encrypted octets:\n" + base64EncodedEncryptedOctets);
        }

        return Base64.getMimeDecoder().decode(base64EncodedEncryptedOctets);
    }
}
