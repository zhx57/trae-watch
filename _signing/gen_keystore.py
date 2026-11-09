"""生成固定的 Android 签名 keystore（PKCS12），供 CI 使用。"""
import datetime
import os
import base64

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

OUT_DIR = r"C:\Users\zhx\Documents\Trae-CN\temp_20261107_02\watch-trae\_signing"
os.makedirs(OUT_DIR, exist_ok=True)

PASSWORD = "TraeWatch2026!Sign"
ALIAS = "trae-watch"

# 1. 生成 RSA 2048 私钥（Android 签名标准）
key = rsa.generate_private_key(public_exponent=65537, key_size=2048)

# 2. 自签名证书，有效期 30 年（Play 要求至少到 2033）
subject = issuer = x509.Name([
    x509.NameAttribute(NameOID.COUNTRY_NAME, "CN"),
    x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, "Beijing"),
    x509.NameAttribute(NameOID.LOCALITY_NAME, "Beijing"),
    x509.NameAttribute(NameOID.ORGANIZATION_NAME, "TraeWatch"),
    x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, "TraeWatch"),
    x509.NameAttribute(NameOID.COMMON_NAME, "Trae Watch"),
])

now = datetime.datetime.now(datetime.timezone.utc)
cert = (
    x509.CertificateBuilder()
    .subject_name(subject)
    .issuer_name(issuer)
    .public_key(key.public_key())
    .serial_number(x509.random_serial_number())
    .not_valid_before(now - datetime.timedelta(days=1))
    .not_valid_after(now + datetime.timedelta(days=365 * 30))
    .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
    .sign(key, hashes.SHA256())
)

# 3. 打包成 PKCS12（Android 支持）
p12_bytes = pkcs12.serialize_key_and_certificates(
    name=ALIAS.encode(),
    key=key,
    cert=cert,
    cas=None,
    encryption_algorithm=serialization.BestAvailableEncryption(PASSWORD.encode()),
)

p12_path = os.path.join(OUT_DIR, "release.p12")
with open(p12_path, "wb") as f:
    f.write(p12_bytes)

# 4. 输出 base64 供 GitHub Secret 使用
b64 = base64.b64encode(p12_bytes).decode()
b64_path = os.path.join(OUT_DIR, "release.p12.b64")
with open(b64_path, "w") as f:
    f.write(b64)

# 5. 打印指纹用于核对
import hashlib
fp = hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()

print("=== keystore 已生成 ===")
print("路径      :", p12_path)
print("大小      :", len(p12_bytes), "bytes")
print("别名      :", ALIAS)
print("密码      :", PASSWORD)
print("SHA-256   :", fp)
print("有效期至  :", (now + datetime.timedelta(days=365 * 30)).date())
print("base64长度:", len(b64))
print("\nbase64 文件:", b64_path)
