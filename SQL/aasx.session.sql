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

DROP TABLE files;
DROP TABLE files_meta;

SELECT * FROM files;
SELECT * FROM files_meta;
