package com.example.medicalapp.model

data class IDCardInfo(
    var name: String = "",
    var idNumber: String = "",
    var gender: String = "",
    var nation: String = "",
    var birthDate: String = "",
    var address: String = "",
    var issuingAuthority: String = "",
    var validPeriod: String = "",
    var photoUri: String? = null
) {
    fun isValid(): Boolean {
        return name.isNotBlank() && idNumber.isNotBlank() && idNumber.length == 18
    }
    
    fun getDisplayInfo(): String {
        return """
            Name: $name
            ID: $idNumber
            Gender: $gender
            Nation: $nation
            Birth: $birthDate
            Address: $address
        """.trimIndent()
    }
}
