CREATE TABLE author (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(200)  NOT NULL,
    country VARCHAR(2)    NOT NULL
);

CREATE INDEX idx_author_country ON author (country);

CREATE TABLE book (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id    BIGINT         NOT NULL REFERENCES author (id),
    title        VARCHAR(300)   NOT NULL,
    price        NUMERIC(10, 2) NOT NULL,
    published_at DATE           NOT NULL
);

CREATE INDEX idx_book_author_id ON book (author_id);

CREATE TABLE review (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id    BIGINT       NOT NULL REFERENCES book (id),
    rating     INT          NOT NULL CHECK (rating BETWEEN 1 AND 5),
    text       VARCHAR(2000),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_review_book_id ON review (book_id);
