use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use jsonwebtoken::{DecodingKey, Algorithm, EncodingKey, Header};
use jsonwebtoken::jwk::Jwk;
use serde_json::{Map, Value};
use sd_jwt_rs::error::Error;
use sd_jwt_rs::{
    SDJWTSerializationFormat,
    SDJWTVerifier,
    SDJWTIssuer,
    ClaimsForSelectiveDisclosureStrategy,
    SDJWTHolder,
};

/// SDJWTError represents all possible errors that may occur while
/// working with JWT in a software-defined manner.
///
/// Each variant of the enum is a unique error case, with a specific
/// error message associated to it.
#[derive(Debug, thiserror::Error, strum::IntoStaticStr)]
pub enum SDJWTError {
    /// Represents an error that occurs when a value cannot be converted
    /// to the expected type. The error message gives more detail about the failure.
    #[error("conversion error: Cannot convert to {message}")]
    ConversionError {
        message: String
    },

    /// This error occurs when the input data could not be deserialized properly.
    /// The error message typically includes the reason for failure.
    #[error("invalid input: {message}")]
    DeserializationError {
        message: String
    },

    /// This error occurs when a data field is not conforming to the expected field.
    /// The error message provides detail about the mismatch.
    #[error("data field is not expected: {message}")]
    DataFieldMismatch {
        message: String
    },

    /// Represents an error that occurs when the same digest appears multiple times.
    /// The digest value is included in the error message.
    #[error("Digest {message} appears multiple times")]
    DuplicateDigestError {
        message: String
    },

    /// This error is raised when a key value appears multiple times where it shouldn't.
    /// The error message identifies the duplicate key.
    #[error("Key {message} appears multiple times")]
    DuplicateKeyError {
        message: String
    },

    /// Represents an error that occurs when a disclosure is found to be invalid.
    #[error("invalid disclosure: {message}")]
    InvalidDisclosure {
        message: String
    },

    /// This error occurs when an invalid array disclosure object is provided.
    #[error("invalid array disclosure: {message}")]
    InvalidArrayDisclosureObject {
        message: String
    },

    /// Represents an error that occurs if a given path is invalid.
    #[error("invalid path: {message}")]
    InvalidPath {
        message: String
    },

    /// This error is raised when an array index is out of bounds.
    /// The error message includes the index and the array's length.
    #[error("index {idx} is out of bounds for the provided array with length {length}: {msg}")]
    IndexOutOfBounds {
        idx: i32,
        length: i32,
        msg: String,
    },

    /// Represents an error that occurs if a state is invalid.
    #[error("invalid state: {message}")]
    InvalidState {
        message: String
    },

    /// This error is indicated when the program ends up in a state where
    /// it receives an Invalid input.
    #[error("invalid input: {message}")]
    InvalidInput {
        message: String
    },

    /// This error occurs when a key is not found where it is expected to be.
    #[error("key not found: {message}")]
    KeyNotFound {
        message: String
    },

    /// This error is used for cases that don't fall under the other error variants.
    /// A descriptive message is included describing the error situation.
    #[error("{message}")]
    Unspecified {
        message: String
    },
}

impl Into<SDJWTError> for serde_json::Error {
    fn into(self) -> SDJWTError {
        SDJWTError::ConversionError { message: self.to_string() }
    }
}

impl Into<SDJWTError> for Error {
    fn into(self) -> SDJWTError {
        match self {
            Error::ConversionError(message) => SDJWTError::ConversionError { message },
            Error::DeserializationError(message) => SDJWTError::DeserializationError { message },
            Error::DataFieldMismatch(message) => SDJWTError::DataFieldMismatch { message },
            Error::DuplicateDigestError(message) => SDJWTError::DuplicateDigestError { message },
            Error::DuplicateKeyError(message) => SDJWTError::DuplicateKeyError { message },
            Error::InvalidDisclosure(message) => SDJWTError::InvalidDisclosure { message },
            Error::InvalidArrayDisclosureObject(message) => SDJWTError::InvalidArrayDisclosureObject { message },
            Error::InvalidPath(message) => SDJWTError::InvalidPath { message },
            Error::IndexOutOfBounds { idx, length, msg } => SDJWTError::IndexOutOfBounds { idx: idx as i32, length: length as i32, msg },
            Error::InvalidState(message) => SDJWTError::InvalidState { message },
            Error::InvalidInput(message) => SDJWTError::InvalidInput { message },
            Error::KeyNotFound(message) => SDJWTError::KeyNotFound { message },
            Error::Unspecified(message) => SDJWTError::Unspecified { message },
            _ => SDJWTError::Unspecified { message: "Unknown Error".to_string() }
        }
    }
}

/// Wrapper for [EncodingKey]
#[derive(Clone)]
pub struct EncodingKeyValue {
    core: EncodingKey,
}

impl EncodingKeyValue {

    /// If you are loading a ECDSA key from a .pem file
    /// This errors if the key is not a valid private EC key
    /// Only exists if the feature `use_pem` is enabled.
    ///
    /// # NOTE
    ///
    /// The key should be in PKCS#8 form.
    ///
    /// You can generate a key with the following:
    ///
    /// ```sh
    /// openssl ecparam -genkey -noout -name prime256v1 \
    ///     | openssl pkcs8 -topk8 -nocrypt -out ec-private.pem
    /// ```
    pub fn new(key: String) -> Result<EncodingKeyValue, SDJWTError> {
        let core = EncodingKey::from_ec_pem(key.clone().as_bytes())
            .map_err(|err| SDJWTError::Unspecified { message: err.to_string() })?;
        Ok(EncodingKeyValue { core })
    }

    /// Creates a new instance of EncodingKeyValue from a secret key.
    ///
    /// # Arguments
    ///
    /// * `secret` - A string that represents the secret key.
    ///
    /// # Returns
    ///
    /// * An instance of EncodingKeyValue.
    pub fn from_secret(secret: String) -> EncodingKeyValue {
        let core = EncodingKey::from_secret(secret.clone().as_bytes());
        EncodingKeyValue { core }
    }

    /// Creates a new instance of EncodingKeyValue from a base64 secret key.
    ///
    /// # Arguments
    ///
    /// * `b64` - A string that represents the base64 secret key.
    ///
    /// # Returns
    ///
    /// * A result which is either an instance of EncodingKeyValue or a SDJWTError.
    pub fn from_base64_secret(b64: String) -> Result<EncodingKeyValue, SDJWTError> {
        let core = EncodingKey::from_base64_secret(b64.clone().as_str())
            .map_err(|err| SDJWTError::Unspecified { message: err.to_string() })?;
        Ok(EncodingKeyValue { core })
    }

    /// Creates a new instance of EncodingKeyValue from a RSA PEM key.
    ///
    /// # Arguments
    ///
    /// * `rsa_pem` - A string that represents the RSA PEM key.
    ///
    /// # Returns
    ///
    /// * A result which is either an instance of EncodingKeyValue or a SDJWTError.
    pub fn from_rsa_pem(rsa_pem: String) -> Result<EncodingKeyValue, SDJWTError> {
        let core = EncodingKey::from_rsa_pem(rsa_pem.clone().as_bytes())
            .map_err(|err| SDJWTError::Unspecified { message: err.to_string() })?;
        Ok(EncodingKeyValue { core })
    }

    /// Creates a new instance of EncodingKeyValue from a RSA DER key.
    ///
    /// # Arguments
    ///
    /// * `rsa_der` - A string that represents the RSA DER key.
    ///
    /// # Returns
    ///
    /// * An instance of EncodingKeyValue.
    pub fn from_rsa_der(rsa_der: String) -> EncodingKeyValue {
        let core = EncodingKey::from_rsa_der(rsa_der.clone().as_bytes());
        EncodingKeyValue { core }
    }

    /// If you are loading a ECDSA key from a .pem file
    /// This errors if the key is not a valid private EC key
    /// Only exists if the feature `use_pem` is enabled.
    ///
    /// # NOTE
    ///
    /// The key should be in PKCS#8 form.
    ///
    /// You can generate a key with the following:
    ///
    /// ```sh
    /// openssl ecparam -genkey -noout -name prime256v1 \
    ///     | openssl pkcs8 -topk8 -nocrypt -out ec-private.pem
    /// ```
    pub fn from_ec_pem(ec_pem: String) -> Result<EncodingKeyValue, SDJWTError> {
        let core = EncodingKey::from_ec_pem(ec_pem.clone().as_bytes())
            .map_err(|err| SDJWTError::Unspecified { message: err.to_string() })?;
        Ok(EncodingKeyValue { core })
    }

    /// This function creates an `EncodingKeyValue` from a PEM-encoded Ed25519 public key string.
    ///
    /// # Arguments
    ///
    /// * `ed_pem`: a `String` that holds the PEM-encoded Ed25519 public key.
    ///
    /// # Returns
    ///
    /// * `Result<EncodingKeyValue, SDJWTError>`: an `Result` which is an `Ok`
    /// if the `EncodingKeyValue` was successfully created, else returns an `Err` with `SDJWTError`.
    pub fn from_ed_pem(ed_pem: String) -> Result<EncodingKeyValue, SDJWTError> {
        let core = EncodingKey::from_ed_pem(ed_pem.clone().as_bytes())
            .map_err(|err| SDJWTError::Unspecified { message: err.to_string() })?;
        Ok(EncodingKeyValue { core })
    }

    /// This function creates an `EncodingKeyValue` from a DER-encoded ECDSA public key string.
    ///
    /// # Arguments
    ///
    /// * `ec_der`: a `String` that holds the DER-encoded ECDSA public key.
    ///
    /// # Returns
    ///
    /// * `EncodingKeyValue`: an `EncodingKeyValue` instance.
    pub fn from_ec_der(ec_der: String) -> EncodingKeyValue {
        let core = EncodingKey::from_ec_der(ec_der.clone().as_bytes());
        EncodingKeyValue { core }
    }

    /// This function creates an `EncodingKeyValue` from a DER-encoded Ed25519 public key string.
    ///
    /// # Arguments
    ///
    /// * `ed_der`: a `String` that holds the DER-encoded Ed25519 public key.
    ///
    /// # Returns
    ///
    /// * `EncodingKeyValue`: an `EncodingKeyValue` instance.
    pub fn from_ed_der(ed_der: String) -> EncodingKeyValue {
        let core = EncodingKey::from_ed_der(ed_der.clone().as_bytes());
        EncodingKeyValue { core }
    }
}

impl From<EncodingKeyValue> for EncodingKey {
    fn from(value: EncodingKeyValue) -> Self {
        return value.core;
    }
}

impl From<EncodingKey> for EncodingKeyValue {
    fn from(value: EncodingKey) -> Self {
        Self {
            core: value
        }
    }
}

/// Wrapper for [Jwk]
#[derive(Clone)]
pub struct JwkValue {
    pub core: Jwk,
}

impl JwkValue {
    /// Create an instance of [JwkValue]
    pub fn new(jwk_json: String) -> Result<Self, SDJWTError> {
        let core = serde_json::from_str(&jwk_json)
            .map_err(|err| err.into())?;
            // .map_err(|err| SDJWTError::Unspecified { message: err.to_string()})?;
        Ok(Self {
            core
        })
    }

    /// get Actual [Jwk] value in JSON String
    pub fn get_json(&self) -> String {
        return serde_json::to_string(&self.core).unwrap();
    }
}

impl From<Jwk> for JwkValue {
    fn from(value: Jwk) -> Self {
        Self {
            core: value
        }
    }
}

impl From<JwkValue> for Jwk {
    fn from(value: JwkValue) -> Self {
        return value.core;
    }
}

/// Wrapper for [SDJWTIssuer]
pub struct SDJWTIssuerWrapper {
    wrapped: Arc<Mutex<SDJWTIssuer>>,
}

impl SDJWTIssuerWrapper {
    /// Creates a new [SDJWTIssuerWrapper] instance.
    pub fn new(issuer_key: Arc<EncodingKeyValue>, sign_alg: Option<String>) -> Self {
        let key_clone = issuer_key.core.clone();
        let wrapped = SDJWTIssuer::new(key_clone, sign_alg);
        Self {
            wrapped: Arc::new(Mutex::from(wrapped))
        }
    }

    /// Issues a SD-JWT with no sd claims
    ///
    /// # Arguments
    /// * `user_claims` - The claims to be included in the SD-JWT.
    /// * `holder_key` - The key used to sign the SD-JWT. If not provided, no key binding is added to the SD-JWT.
    /// * `add_decoy_claims` - If true, decoy claims are added to the SD-JWT.
    /// * `serialization_format` - The serialization format to be used for the SD-JWT, see [SDJWTSerializationFormat].
    ///
    /// # Returns
    /// The issued SD-JWT as a string in the requested serialization format.
    pub fn issue_sd_jwt_no_sd_claims(
        &self,
        user_claims: String,
        holder_key: Option<Arc<JwkValue>>,
        add_decoy_claims: bool,
        serialization_format: SDJWTSerializationFormat,
    ) -> Result<String, SDJWTError> {
        let user_claims_value = serde_json::from_str(user_claims.as_str()).map_err(|err| SDJWTError::Unspecified { message: err.to_string()})?;
        let mut locked = self.wrapped.lock().unwrap();
        return if let Some(val) = holder_key {
            locked.issue_sd_jwt(user_claims_value, ClaimsForSelectiveDisclosureStrategy::NoSDClaims, Option::from(val.core.clone()), add_decoy_claims, serialization_format)
                .map_err(|err| err.into())
        } else {
            locked.issue_sd_jwt(user_claims_value, ClaimsForSelectiveDisclosureStrategy::NoSDClaims, None, add_decoy_claims, serialization_format)
                .map_err(|err| err.into())
        };
    }

    /// Issues a SD-JWT with Top Level
    ///
    /// # Arguments
    /// * `user_claims` - The claims to be included in the SD-JWT.
    /// * `holder_key` - The key used to sign the SD-JWT. If not provided, no key binding is added to the SD-JWT.
    /// * `add_decoy_claims` - If true, decoy claims are added to the SD-JWT.
    /// * `serialization_format` - The serialization format to be used for the SD-JWT, see [SDJWTSerializationFormat].
    ///
    /// # Returns
    /// The issued SD-JWT as a string in the requested serialization format.
    pub fn issue_sd_jwt_top_level(
        &self,
        user_claims: String,
        holder_key: Option<Arc<JwkValue>>,
        add_decoy_claims: bool,
        serialization_format: SDJWTSerializationFormat,
    ) -> Result<String, SDJWTError> {
        let user_claims_value = serde_json::from_str(user_claims.as_str()).map_err(|err| SDJWTError::Unspecified { message: err.to_string()})?;
        let mut locked = self.wrapped.lock().unwrap();
        return if let Some(val) = holder_key {
            locked.issue_sd_jwt(user_claims_value, ClaimsForSelectiveDisclosureStrategy::TopLevel, Option::from(val.core.clone()), add_decoy_claims, serialization_format)
                .map_err(|err| err.into())
        } else {
            locked.issue_sd_jwt(user_claims_value, ClaimsForSelectiveDisclosureStrategy::TopLevel, None, add_decoy_claims, serialization_format)
                .map_err(|err| err.into())
        };
    }

    /// Issues a SD-JWT with All Levels
    ///
    /// # Arguments
    /// * `user_claims` - The claims to be included in the SD-JWT.
    /// * `holder_key` - The key used to sign the SD-JWT. If not provided, no key binding is added to the SD-JWT.
    /// * `add_decoy_claims` - If true, decoy claims are added to the SD-JWT.
    /// * `serialization_format` - The serialization format to be used for the SD-JWT, see [SDJWTSerializationFormat].
    ///
    /// # Returns
    /// The issued SD-JWT as a string in the requested serialization format.
    pub fn issue_sd_jwt_all_level(
        &self,
        user_claims: String,
        holder_key: Option<Arc<JwkValue>>,
        add_decoy_claims: bool,
        serialization_format: SDJWTSerializationFormat,
    ) -> Result<String, SDJWTError> {
        let user_claims_value = serde_json::from_str(user_claims.as_str()).map_err(|err| SDJWTError::Unspecified { message: err.to_string()})?;
        let mut locked = self.wrapped.lock().unwrap();
        return if let Some(val) = holder_key {
            locked.issue_sd_jwt(user_claims_value, ClaimsForSelectiveDisclosureStrategy::AllLevels, Option::from(val.core.clone()), add_decoy_claims, serialization_format)
                .map_err(|err| err.into())
        } else {
            locked.issue_sd_jwt(user_claims_value, ClaimsForSelectiveDisclosureStrategy::AllLevels, None, add_decoy_claims, serialization_format)
                .map_err(|err| err.into())
        };
    }

    /// Issues a SD-JWT with All Levels
    ///
    /// # Arguments
    /// * `user_claims` - The claims to be included in the SD-JWT.
    /// * `json_paths` - Claims to be selectively disclosed based on the provided JSONPaths.
    /// * `holder_key` - The key used to sign the SD-JWT. If not provided, no key binding is added to the SD-JWT.
    /// * `add_decoy_claims` - If true, decoy claims are added to the SD-JWT.
    /// * `serialization_format` - The serialization format to be used for the SD-JWT, see [SDJWTSerializationFormat].
    ///
    /// # Returns
    /// The issued SD-JWT as a string in the requested serialization format.
    pub fn issue_sd_jwt_custom(
        &self,
        user_claims: String,
        json_paths: Vec<String>,
        holder_key: Option<Arc<JwkValue>>,
        add_decoy_claims: bool,
        serialization_format: SDJWTSerializationFormat,
    ) -> Result<String, SDJWTError> {
        let user_claims_value = serde_json::from_str(user_claims.as_str()).map_err(|err| SDJWTError::Unspecified { message: err.to_string()})?;
        let json_paths_right_data_type = json_paths.iter().map(|s| s.as_str()).collect();
        let mut locked = self.wrapped.lock().unwrap();
        return if let Some(val) = holder_key {
            locked.issue_sd_jwt(user_claims_value, ClaimsForSelectiveDisclosureStrategy::Custom(json_paths_right_data_type), Option::from(val.core.clone()), add_decoy_claims, serialization_format)
                .map_err(|err| err.into())
        } else {
            locked.issue_sd_jwt(user_claims_value, ClaimsForSelectiveDisclosureStrategy::Custom(json_paths_right_data_type), None, add_decoy_claims, serialization_format)
                .map_err(|err| err.into())
        };
    }
}

/// Wrapper for [SDJWTHolder]
pub struct SDJWTHolderWrapper {
    pub wrapped: Arc<Mutex<SDJWTHolder>>,
}

impl SDJWTHolderWrapper {
    /// Creates a new [SDJWTHolderWrapper] instance.
    pub fn new(
        sd_jwt_with_disclosures: String,
        serialization_format: SDJWTSerializationFormat,
    ) -> Result<Self, SDJWTError> {
        let wrapped = SDJWTHolder::new(sd_jwt_with_disclosures, serialization_format)
            .map_err(|err| SDJWTError::Unspecified { message: err.to_string()})?;
        Ok(Self {
            wrapped: Arc::new(Mutex::new(wrapped))
        })
    }

    /// Create a presentation based on the SD JWT provided by issuer.
    ///
    /// # Arguments
    /// * `claims_to_disclose_json` - Claims to disclose in the presentation
    /// * `nonce` - Nonce to be used in the key-binding JWT
    /// * `aud` - Audience to be used in the key-binding JWT
    /// * `holder_key` - Key to sign the key-binding JWT
    /// * `sign_alg` - Signing algorithm to be used in the key-binding JWT
    ///
    /// # Returns
    /// * `String` - Presentation in the format specified by `serialization_format` in the constructor. It can be either compact or json.
    pub fn create_presentation(
        &self,
        claims_to_disclose_json: String,
        nonce: Option<String>,
        aud: Option<String>,
        holder_key: Option<Arc<EncodingKeyValue>>,
        sign_alg: Option<String>,
    ) -> Result<String, SDJWTError> {

        let claims_to_disclose_value: Value = serde_json::from_str(claims_to_disclose_json.as_str()).unwrap();
        let claims_to_disclose_map = claims_to_disclose_value.as_object().unwrap().to_owned();
        let claims_to_disclose_hash_map = self.convert(claims_to_disclose_map);
        let mut map_value: Map<String, Value> = Map::new();
        for (key, value) in claims_to_disclose_hash_map {
            let maybe_json_value: Result<Value, _> = serde_json::from_str(&value);
            let mut value_to_insert = maybe_json_value.unwrap_or_else(|_err| {
                Value::String(value.clone())
            });
            if value_to_insert.is_number() {
                value_to_insert = Value::String(value.clone())
            }
            map_value.insert(key.clone(), value_to_insert);
        }

        let mut locked = self.wrapped.lock().unwrap();
        return if let Some(val) = holder_key {
            locked.create_presentation(map_value, nonce, aud, Option::from(val.core.clone()), sign_alg)
                .map_err(|err| err.into())
        } else {
            locked.create_presentation(map_value, nonce, aud, None, sign_alg)
                .map_err(|err| err.into())
        };
    }

    /// Converts a `Map<String, Value>` into a `HashMap<String, String>`.
    ///
    /// This function iterates over the elements of the provided map. For each key-value pair:
    ///
    /// - If the value is a `String`, it is cloned and inserted into the hashmap with its corresponding key.
    /// - If the value is a number or an object, it is converted to a string representation and then inserted into the hashmap with its corresponding key.
    ///
    /// # Arguments
    ///
    /// * `map` - A `Map<String, Value>` that will be converted into a `HashMap<String, String>`.
    ///
    /// # Returns
    ///
    /// This function returns a `HashMap<String, String>` which represents the converted map.
    ///
    /// # Panics
    ///
    /// The function panics with the message "Should never be an error here" if a value that is not a `String`, number or object is found in the map.
    fn convert(&self, map: Map<String, Value>) -> HashMap<String, String> {
        let mut hashmap: HashMap<String, String> = HashMap::new();

        for (key, value) in map {
            if let Value::String(str_val) = value {
                hashmap.insert(key, str_val.clone());
            } else if value.is_number() {
                hashmap.insert(key, value.to_string());
            } else if value.is_object() {
                hashmap.insert(key, value.to_string());
            } else {
                panic!("Should never be an error here")
            }
        }

        hashmap
    }
}

// unsafe impl Sync for SDJWTVerifier {}

/// Wrapper for Verifier
pub struct SDJWTVerifierWrapper {
    pub wrapped: Arc<SDJWTVerifier>,
}

unsafe impl Send for SDJWTVerifierWrapper {}
unsafe impl Sync for SDJWTVerifierWrapper {}

impl SDJWTVerifierWrapper {
    /// Creates a new instance of `SDJWTVerifier` with the specified parameters.
    ///
    /// # Arguments
    ///
    /// * `sd_jwt_presentation` - parameter represents the JWT presentation that needs to be verified.
    /// * `public_key` - parameter represents the public key used for verification. It should be provided as a string.
    /// * `expected_aud` - parameter specifies the expected value for the `aud` claim in the JWT. It is an optional parameter.
    /// * `expected_nonce` - parameter specifies the expected value for the `nonce` claim in the JWT. It is an optional parameter.
    /// * `serialization_format` - parameter represents the serialization format used for the JWT.
    pub fn new(
        sd_jwt_presentation: String,
        public_key: String,
        expected_aud: Option<String>,
        expected_nonce: Option<String>,
        serialization_format: SDJWTSerializationFormat,
    ) -> Result<Self, SDJWTError> {
        let public_key_bytes = public_key.into_bytes();
        let public_issuer_bytes = Arc::new(Mutex::new(public_key_bytes));
        let wrap_value = SDJWTVerifier::new(
            sd_jwt_presentation,
            Box::new(move |_: &str, header: &Header| {
                let public_issuer_bytes = public_issuer_bytes.lock().unwrap();
                match header.alg {
                    Algorithm::ES256 | Algorithm::ES384 => 
                        DecodingKey::from_ec_pem(public_issuer_bytes.as_slice()).unwrap(),
                    Algorithm::RS256 | Algorithm::RS384 | Algorithm::RS512 => 
                        DecodingKey::from_rsa_pem(public_issuer_bytes.as_slice()).unwrap(),
                    Algorithm::EdDSA => 
                        DecodingKey::from_ed_pem(public_issuer_bytes.as_slice()).unwrap(),
                    _ => panic!("Unsupported algorithm"),
                }
            }) as Box<dyn Fn(&str, &Header) -> DecodingKey>,
            expected_aud,
            expected_nonce,
            serialization_format,
        ).map_err(|err| SDJWTError::Unspecified { message: err.to_string()})?;
        Ok(Self {
            wrapped: Arc::new(wrap_value)
        })
    }

    /// Return Verified Claims
    pub fn get_verified_claims(&self) -> String {
        return self.wrapped.verified_claims.to_string();
    }

    /// Verifies user claims.
    ///
    /// This function takes a JSON string of user claims, parses it into an object, and compares it with a verified claims object.
    ///
    /// # Arguments
    ///
    /// * `user_claims_json` - A JSON string representing the user claims to verify.
    ///
    /// # Returns
    ///
    /// This function returns a boolean value indicating whether the user claims are equal to the verified claims.
    ///
    /// # Panics
    ///
    /// The function will panic with a descriptive error message if it fails to parse the JSON for user or verified claims.
    pub fn verify(&self, user_claims_json: String) -> bool {
        let user_claims_json_obj: Value = serde_json::from_str(&user_claims_json).unwrap();
        let user_claims_map: &Map<String, Value> = user_claims_json_obj.as_object().unwrap();

        let verified_claims: String = self.get_verified_claims();
        let verified_claims_json_obj: Value = serde_json::from_str(&verified_claims).unwrap();
        let verified_claims_map: &Map<String, Value> = verified_claims_json_obj.as_object().unwrap();

        return user_claims_map.eq(verified_claims_map);
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;
    use log::trace;
    use serde_json::Value;
    use sd_jwt_rs::SDJWTSerializationFormat;
    use crate::wrapper::{EncodingKeyValue, SDJWTHolderWrapper, SDJWTIssuerWrapper, SDJWTVerifierWrapper};

    const PRIVATE_ISSUER_PEM: &str = "-----BEGIN PRIVATE KEY-----\nMIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgUr2bNKuBPOrAaxsR\nnbSH6hIhmNTxSGXshDSUD1a1y7ihRANCAARvbx3gzBkyPDz7TQIbjF+ef1IsxUwz\nX1KWpmlVv+421F7+c1sLqGk4HUuoVeN8iOoAcE547pJhUEJyf5Asc6pP\n-----END PRIVATE KEY-----\n";
    const PUBLIC_ISSUER_PEM: &str = "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEb28d4MwZMjw8+00CG4xfnn9SLMVM\nM19SlqZpVb/uNtRe/nNbC6hpOB1LqFXjfIjqAHBOeO6SYVBCcn+QLHOqTw==\n-----END PUBLIC KEY-----\n";

    #[test]
    fn test_assembly_sd_full_recursive() {
        let user_claims: String = r#"
        {
            "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
            "iss": "https://example.com/issuer",
            "iat": 1683000000,
            "exp": 1883000000,
            "address": {
                "street_address": "Schulstr. 12",
                "locality": "Schulpforta",
                "region": "Sachsen-Anhalt",
                "country": "DE"
            }
        }
        "#.chars().filter(|&c| c != ' ' && c != '\n').collect();
        let issuer_key = Arc::new(EncodingKeyValue::new(PRIVATE_ISSUER_PEM.to_string()).unwrap());
        let sd_jwt = SDJWTIssuerWrapper::new(issuer_key, Option::from("ES256".to_string())).issue_sd_jwt_all_level(
            user_claims,
            None,
            false,
            SDJWTSerializationFormat::Compact,
        ).unwrap();
        trace!("{:?}", sd_jwt)
    }

    #[test]
    fn create_full_presentation() {
        let user_claims: String = r#"
        {
            "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
            "iss": "https://example.com/issuer",
            "iat": 1683000000,
            "exp": 1883000000,
            "address": {
                "street_address": "Schulstr. 12",
                "locality": "Schulpforta",
                "region": "Sachsen-Anhalt",
                "country": "DE"
            }
        }
        "#.chars().filter(|&c| c != ' ' && c != '\n').collect();
        let issuer_key = Arc::new(
            EncodingKeyValue::new(PRIVATE_ISSUER_PEM.to_string()).unwrap()
        );
        let sd_jwt = SDJWTIssuerWrapper::new(
            issuer_key,
            None,
        ).issue_sd_jwt_all_level(
            user_claims.clone(),
            None,
            false,
            SDJWTSerializationFormat::Compact,
        ).unwrap();

        let claims_to_disclose_json: String = r#"
        {
            "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
            "iss": "https://example.com/issuer",
            "iat": 1683000000,
            "exp": 1883000000,
            "address": {
                "street_address": "Schulstr. 12",
                "locality": "Schulpforta",
                "region": "Sachsen-Anhalt",
                "country": "DE"
            }
        }
        "#.chars().filter(|&c| c != ' ' && c != '\n').collect();
        let presentation = SDJWTHolderWrapper::new(
            sd_jwt.clone(),
            SDJWTSerializationFormat::Compact,
        )
            .unwrap()
            .create_presentation(
                claims_to_disclose_json.clone(),
                None,
                None,
                None,
                None,
            )
            .unwrap();
        let presentation_split: Vec<&str> = presentation.split('~').collect();
        let sdjwt_split: Vec<&str> = sd_jwt.split('~').collect();
        assert_eq!(sdjwt_split.len(), presentation_split.len());
        for i in &presentation_split {
            assert!(sdjwt_split.contains(i));
        }
    }

    #[test]
    fn test_verifier() {
        let user_claims: String = r#"
        {
            "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
            "iss": "https://example.com/issuer",
            "iat": 1683000000,
            "exp": 1883000000,
            "address": {
                "street_address": "Schulstr. 12",
                "locality": "Schulpforta",
                "region": "Sachsen-Anhalt",
                "country": "DE"
            }
        }
        "#.chars().filter(|&c| c != ' ' && c != '\n').collect();
        let issuer_key = Arc::new(
            EncodingKeyValue::new(PRIVATE_ISSUER_PEM.to_string()).unwrap()
        );
        let sd_jwt = SDJWTIssuerWrapper::new(
            issuer_key,
            None,
        ).issue_sd_jwt_all_level(
            user_claims.clone(),
            None,
            false,
            SDJWTSerializationFormat::Compact,
        ).unwrap();

        let claims_to_disclose_json: String = r#"
        {
            "sub": "6c5c0a49-b589-431d-bae7-219122a9ec2c",
            "iss": "https://example.com/issuer",
            "iat": 1683000000,
            "exp": 1883000000,
            "address": {
                "street_address": "Schulstr. 12",
                "locality": "Schulpforta",
                "region": "Sachsen-Anhalt",
                "country": "DE"
            }
        }
        "#.chars().filter(|&c| c != ' ' && c != '\n').collect();
        let presentation = SDJWTHolderWrapper::new(
            sd_jwt.clone(),
            SDJWTSerializationFormat::Compact,
        )
            .unwrap()
            .create_presentation(
                claims_to_disclose_json.clone(),
                None,
                None,
                None,
                None,
            )
            .unwrap();
        let presentation_split: Vec<&str> = presentation.split('~').collect();
        let sdjwt_split: Vec<&str> = sd_jwt.split('~').collect();
        assert_eq!(sdjwt_split.len(), presentation_split.len());
        for i in &presentation_split {
            assert!(sdjwt_split.contains(i));
        }

        let verified_claims = SDJWTVerifierWrapper::new(
            presentation,
            PUBLIC_ISSUER_PEM.to_string(),
            None,
            None,
            SDJWTSerializationFormat::Compact,
        ).unwrap().get_verified_claims();

        let user_claims_json_obj: Value = serde_json::from_str(&user_claims).unwrap();
        let user_claims_map: &serde_json::map::Map<String, Value> = user_claims_json_obj.as_object().unwrap();

        let verified_claims_json_obj: Value = serde_json::from_str(&verified_claims).unwrap();
        let verified_claims_map: &serde_json::map::Map<String, Value> = verified_claims_json_obj.as_object().unwrap();

        assert_eq!(user_claims_map.eq(verified_claims_map), true);
        // They return JSON String which we can't guarantee its keys order
        // So we parse that JSON then convert it to Map Object then compare
        // assert_eq!(user_claims, verified_claims);
    }
}
