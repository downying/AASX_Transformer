CREATE TABLE files
(
  hash      TEXT    NOT NULL,
  ref_count INTEGER,
  PRIMARY KEY (hash)
);

CREATE TABLE files_meta
(
  path         TEXT    NOT NULL,
  name         TEXT    NOT NULL,
  extension    TEXT    NOT NULL,
  content_type TEXT    NOT NULL,
  size         INTEGER NOT NULL,
  hash         TEXT    NOT NULL,
  PRIMARY KEY (path),
  FOREIGN KEY (hash) REFERENCES files(hash)
);

/* 외래 키 기능을 활성화 */
PRAGMA foreign_keys = ON;

DROP TABLE files;
DROP TABLE files_meta;
