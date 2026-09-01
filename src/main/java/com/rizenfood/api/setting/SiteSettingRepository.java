package com.rizenfood.api.setting;

import org.springframework.data.jpa.repository.JpaRepository;

/** key 자체가 @Id 라 findById 가 곧 findByKey 다. */
public interface SiteSettingRepository extends JpaRepository<SiteSetting, String> {
}
