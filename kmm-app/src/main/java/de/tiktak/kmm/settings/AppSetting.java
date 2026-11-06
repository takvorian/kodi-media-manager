package de.tiktak.kmm.settings;

import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A stored setting as a key-value pair.
 */
@Entity
@Table(name = "app_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AppSetting {

    @Id
    @Column(name = "setting_key", length = 128)
    private String key;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "setting_value", nullable = false, length = Length.LONG32)
    private String value;

    AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    void changeValue(String newValue) {
        this.value = newValue;
    }
}
