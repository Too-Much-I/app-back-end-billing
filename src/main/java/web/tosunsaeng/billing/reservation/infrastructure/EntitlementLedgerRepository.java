package web.tosunsaeng.billing.reservation.infrastructure;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import web.tosunsaeng.billing.reservation.domain.EntitlementLedgerEntry;

@Repository
public class EntitlementLedgerRepository {

    private final MongoTemplate mongoTemplate;

    public EntitlementLedgerRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public EntitlementLedgerEntry insert(EntitlementLedgerEntry entry) {
        return mongoTemplate.insert(entry);
    }

    public long nextGrantSequence(String grantId) {
        Query query = Query.query(Criteria.where("aggregateType").is("GRANT")
                        .and("aggregateId").is(grantId))
                .with(Sort.by(Sort.Direction.DESC, "sequence"))
                .limit(1);
        EntitlementLedgerEntry latest = mongoTemplate.findOne(
                query, EntitlementLedgerEntry.class
        );
        return latest == null ? 1 : latest.getSequence() + 1;
    }
}
