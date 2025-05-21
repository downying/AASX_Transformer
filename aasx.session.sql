CREATE TABLE files
(
  hash      TEXT    NOT NULL,
  ref_count INTEGER,
  size      INTEGER,
  PRIMARY KEY (hash)
);

CREATE TABLE files_meta
(
  aas_id       TEXT    NOT NULL,
  submodel_id  TEXT    NOT NULL,
  idShort      TEXT    NOT NULL,
  name         TEXT    NOT NULL,
  extension    TEXT    NOT NULL,
  content_type TEXT    NOT NULL,
  hash         TEXT    NOT NULL,
  PRIMARY KEY (aas_id, submodel_id, idShort),
  FOREIGN KEY (hash) REFERENCES files(hash)
);

/* 외래 키 기능을 활성화 */
PRAGMA foreign_keys = ON;

/* drop table */
DROP TABLE files;
DROP TABLE files_meta;

/* test */
SELECT * FROM files;
SELECT * FROM files_meta;

SELECT
    f.hash,
    f.ref_count AS refCount,
    f.size,
    COALESCE(m.extension, '')     AS extension,
    COALESCE(m.content_type, '')  AS contentType
    FROM files f
    LEFT JOIN (
    SELECT hash,
            MIN(extension)     AS extension,
            MIN(content_type)  AS content_type
        FROM files_meta
    GROUP BY hash
    ) m ON f.hash = m.hash;