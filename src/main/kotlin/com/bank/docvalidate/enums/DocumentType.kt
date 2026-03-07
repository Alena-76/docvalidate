package com.bank.docvalidate.enums

enum class DocumentType(val displayName: String, val allowedExtensions: List<String>) {
    NATIONAL_ID("National ID Card", listOf("pdf", "jpg", "jpeg", "png")),
    PASSPORT("Passport", listOf("pdf", "jpg", "jpeg", "png")),
    PAYSLIP("Payslip", listOf("pdf")),
    BANK_STATEMENT("Bank Statement", listOf("pdf")),
    PROOF_OF_ADDRESS("Proof of Address", listOf("pdf", "jpg", "jpeg", "png")),
    TAX_RETURN("Tax Return", listOf("pdf")),
    EMPLOYMENT_CONTRACT("Employment Contract", listOf("pdf")),
    BUSINESS_REGISTRATION("Business Registration", listOf("pdf")),
    OTHER("Other", listOf("pdf", "jpg", "jpeg", "png"))
}
