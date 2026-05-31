package com.document.documentetl.repository;

import com.document.documentetl.model.MlflowTraceBridgeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MlflowTraceBridgeEventRepository extends JpaRepository<MlflowTraceBridgeEvent, String> {
}
