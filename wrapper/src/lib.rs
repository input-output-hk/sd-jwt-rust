mod wrapper;

use crate::wrapper::*;
use sd_jwt_rs::SDJWTSerializationFormat;

uniffi::include_scaffolding!("sdjwtwrapper");
