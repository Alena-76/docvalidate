package com.bank.docvalidate.enums

enum class UserRole {
    ROLE_REVIEWER,      // Can pick up and review documents (second-level)
    ROLE_SUPERVISOR,    // Can handle escalations and override decisions
    ROLE_ADMIN          // Full access, user management
}
