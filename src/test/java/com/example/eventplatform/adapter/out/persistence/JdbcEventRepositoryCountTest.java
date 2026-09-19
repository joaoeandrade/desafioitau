package com.example.eventplatform.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEventRepositoryCountTest {

    @Test
    void countsInsertedRowsIgnoringConflicts() {
        assertThat(JdbcEventRepository.countInserted(new int[][]{{1, 0, 1}, {1}}, 4)).isEqualTo(3);
    }

    @Test
    void fallsBackToSentSizeWhenDriverReportsNoInfo() {
        assertThat(JdbcEventRepository.countInserted(new int[][]{{Statement.SUCCESS_NO_INFO}}, 7)).isEqualTo(7);
    }
}
