package com.example.jooqdemo.repository;

import com.example.jooqdemo.generated.tables.records.AuthorRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.example.jooqdemo.generated.Tables.AUTHOR;

@Repository
@RequiredArgsConstructor
public class AuthorRepository {

    private final DSLContext dsl;

    public List<String> findNamesByCountry(String country) {
        return dsl.select(AUTHOR.NAME)
                .from(AUTHOR)
                .where(AUTHOR.COUNTRY.eq(country))
                .orderBy(AUTHOR.NAME.asc())
                .fetch(AUTHOR.NAME);
    }

    public Optional<AuthorRecord> findByName(String name) {
        return dsl.selectFrom(AUTHOR)
                .where(AUTHOR.NAME.eq(name))
                .fetchOptional();
    }
}
