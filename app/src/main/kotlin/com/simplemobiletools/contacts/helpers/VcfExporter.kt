package com.simplemobiletools.contacts.helpers

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds
import android.provider.MediaStore
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getFileOutputStream
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.extensions.toFileDirItem
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.contacts.R
import com.simplemobiletools.contacts.extensions.getByteArray
import com.simplemobiletools.contacts.extensions.getDateTimeFromDateString
import com.simplemobiletools.contacts.helpers.VcfExporter.ExportResult.EXPORT_FAIL
import com.simplemobiletools.contacts.models.Contact
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.parameter.AddressType
import ezvcard.parameter.EmailType
import ezvcard.parameter.ImageType
import ezvcard.parameter.TelephoneType
import ezvcard.property.*
import ezvcard.util.PartialDate
import java.io.File
import java.util.*

class VcfExporter {
    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    private var contactsExported = 0
    private var contactsFailed = 0

    fun exportContacts(activity: BaseSimpleActivity, file: File, contacts: ArrayList<Contact>, showExportingToast: Boolean, callback: (result: ExportResult) -> Unit) {
        activity.getFileOutputStream(file.toFileDirItem(activity), true) {
            try {
                if (it == null) {
                    callback(EXPORT_FAIL)
                    return@getFileOutputStream
                }

                if (showExportingToast) {
                    activity.toast(R.string.exporting)
                }

                val cards = ArrayList<VCard>()
                for (contact in contacts) {
                    val card = VCard()
                    StructuredName().apply {
                        prefixes.add(contact.prefix)
                        given = contact.firstName
                        additionalNames.add(contact.middleName)
                        family = contact.surname
                        suffixes.add(contact.suffix)
                        card.structuredName = this
                    }

                    if (contact.nickname.isNotEmpty()) {
                        card.setNickname(contact.nickname)
                    }

                    contact.phoneNumbers.forEach {
                        val phoneNumber = Telephone(it.value)
                        phoneNumber.types.add(TelephoneType.find(getPhoneNumberLabel(it.type)))
                        card.addTelephoneNumber(phoneNumber)
                    }

                    contact.emails.forEach {
                        val email = Email(it.value)
                        email.types.add(EmailType.find(getEmailTypeLabel(it.type)))
                        card.addEmail(email)
                    }

                    contact.events.forEach {
                        if (it.type == CommonDataKinds.Event.TYPE_BIRTHDAY || it.type == CommonDataKinds.Event.TYPE_ANNIVERSARY) {
                            val dateTime = it.value.getDateTimeFromDateString()
                            if (it.value.startsWith("--")) {
                                val partialDate = PartialDate.builder().year(null).month(dateTime.monthOfYear - 1).date(dateTime.dayOfMonth).build()
                                if (it.type == CommonDataKinds.Event.TYPE_BIRTHDAY) {
                                    card.birthdays.add(Birthday(partialDate))
                                } else {
                                    card.anniversaries.add(Anniversary(partialDate))
                                }
                            } else {
                                Calendar.getInstance().apply {
                                    clear()
                                    set(Calendar.YEAR, dateTime.year)
                                    set(Calendar.MONTH, dateTime.monthOfYear - 1)
                                    set(Calendar.DAY_OF_MONTH, dateTime.dayOfMonth)
                                    if (it.type == CommonDataKinds.Event.TYPE_BIRTHDAY) {
                                        card.birthdays.add(Birthday(time))
                                    } else {
                                        card.anniversaries.add(Anniversary(time))
                                    }
                                }
                            }
                        }
                    }

                    contact.addresses.forEach {
                        val address = Address()
                        address.streetAddress = it.value
                        address.types.add(AddressType.find(getAddressTypeLabel(it.type)))
                        card.addAddress(address)
                    }

                    if (contact.notes.isNotEmpty()) {
                        card.addNote(contact.notes)
                    }

                    if (!contact.organization.isEmpty()) {
                        val organization = Organization()
                        organization.values.add(contact.organization.company)
                        card.organization = organization
                        card.titles.add(Title(contact.organization.jobPosition))
                    }

                    contact.websites.forEach {
                        card.addUrl(it)
                    }

                    if (contact.thumbnailUri.isNotEmpty()) {
                        val photoByteArray = MediaStore.Images.Media.getBitmap(activity.contentResolver, Uri.parse(contact.thumbnailUri)).getByteArray()
                        val photo = Photo(photoByteArray, ImageType.JPEG)
                        card.addPhoto(photo)
                    }

                    cards.add(card)
                    contactsExported++
                }

                Ezvcard.write(cards).go(file)
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }

            callback(when {
                contactsExported == 0 -> EXPORT_FAIL
                contactsFailed > 0 -> ExportResult.EXPORT_PARTIAL
                else -> ExportResult.EXPORT_OK
            })
        }
    }

    private fun getPhoneNumberLabel(type: Int) = when (type) {
        CommonDataKinds.Phone.TYPE_MOBILE -> CELL
        CommonDataKinds.Phone.TYPE_WORK -> WORK
        CommonDataKinds.Phone.TYPE_MAIN -> PREF
        CommonDataKinds.Phone.TYPE_FAX_WORK -> WORK_FAX
        CommonDataKinds.Phone.TYPE_FAX_HOME -> HOME_FAX
        CommonDataKinds.Phone.TYPE_PAGER -> PAGER
        else -> HOME
    }

    private fun getEmailTypeLabel(type: Int) = when (type) {
        CommonDataKinds.Email.TYPE_WORK -> WORK
        CommonDataKinds.Email.TYPE_MOBILE -> MOBILE
        else -> HOME
    }

    private fun getAddressTypeLabel(type: Int) = when (type) {
        CommonDataKinds.StructuredPostal.TYPE_WORK -> WORK
        else -> HOME
    }
}
