package com.bcm.messenger.common.crypto.storage;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.IdentityKeyUtil;
import com.bcm.messenger.common.crypto.SessionUtil;
import com.bcm.messenger.common.database.records.IdentityRecord;
import com.bcm.messenger.common.database.repositories.IdentityRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.provider.AMESelfData;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.utils.IdentityUtil;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;

public class TextSecureIdentityKeyStore implements IdentityKeyStore {

    private static final int TIMESTAMP_THRESHOLD_SECONDS = 5;

    private static final String TAG = TextSecureIdentityKeyStore.class.getSimpleName();
    private static final Object LOCK = new Object();

    private final Context context;

    public TextSecureIdentityKeyStore(Context context) {
        this.context = context;
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return IdentityKeyUtil.getIdentityKeyPair(context);
    }

    @Override
    public int getLocalRegistrationId() {
        return AMESelfData.INSTANCE.getRegistrationId();
    }

    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey, boolean nonBlockingApproval) {
        synchronized (LOCK) {
            IdentityRepo identityRepo = Repository.getIdentityRepo();
            Address signalAddress = Address.from(context, address.getName());
            IdentityRecord identityRecord = identityRepo.getIdentityRecord(signalAddress.serialize());

            if (identityRecord == null) {
                Log.w(TAG, "Saving new identity...");
                identityRepo.saveIdentity(signalAddress.serialize(), identityKey, IdentityRepo.VerifiedStatus.DEFAULT, true, System.currentTimeMillis(), nonBlockingApproval);
                return false;
            }

            if (!identityRecord.getIdentityKey().equals(identityKey)) {
                Log.w(TAG, "Replacing existing identity...");
                IdentityRepo.VerifiedStatus verifiedStatus;

                if (identityRecord.getVerifyStatus() == IdentityRepo.VerifiedStatus.VERIFIED ||
                        identityRecord.getVerifyStatus() == IdentityRepo.VerifiedStatus.UNVERIFIED) {
                    verifiedStatus = IdentityRepo.VerifiedStatus.UNVERIFIED;
                } else {
                    verifiedStatus = IdentityRepo.VerifiedStatus.DEFAULT;
                }

                identityRepo.saveIdentity(signalAddress.serialize(), identityKey, verifiedStatus, false, System.currentTimeMillis(), nonBlockingApproval);

                String localNumber = AMESelfData.INSTANCE.getUid();
                if (!TextUtils.equals(localNumber, signalAddress.serialize())) {
                    IdentityUtil.markIdentityUpdate(context, Recipient.from(context, signalAddress, true));
                }
                SessionUtil.archiveSiblingSessions(context, address);
                return true;
            }

            if (isNonBlockingApprovalRequired(identityRecord)) {
                Log.w(TAG, "Setting approval status...");

                identityRepo.setApproval(signalAddress.serialize(), nonBlockingApproval);
                return false;
            }

            return false;
        }
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        return saveIdentity(address, identityKey, false);
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        synchronized (LOCK) {
            IdentityRepo identityRepo = Repository.getIdentityRepo();
            String ourNumber = AMESelfData.INSTANCE.getUid();
            Address theirAddress = Address.from(context, address.getName());

            if (ourNumber.equals(address.getName()) || Address.fromSerialized(ourNumber).equals(theirAddress)) {
                return identityKey.equals(IdentityKeyUtil.getIdentityKey(context));
            }

            switch (direction) {
                case SENDING:
                    return isTrustedForSending(identityKey, identityRepo.getIdentityRecord(theirAddress.serialize()));
                case RECEIVING:
                    return true;
                default:
                    throw new AssertionError("Unknown direction: " + direction);
            }
        }
    }

    private boolean isTrustedForSending(IdentityKey identityKey, IdentityRecord identityRecord) {
        if (identityRecord == null) {
            Log.w(TAG, "Nothing here, returning true...");
            return true;
        }

        if (!identityKey.equals(identityRecord.getIdentityKey())) {
            Log.w(TAG, "Identity keys don't match...");
            return false;
        }

        if (identityRecord.getVerifyStatus() == IdentityRepo.VerifiedStatus.UNVERIFIED) {
            Log.w(TAG, "Needs unverified approval!");
            return false;
        }

        if (isNonBlockingApprovalRequired(identityRecord)) {
            Log.w(TAG, "Needs non-blocking approval!");
            return false;
        }

        return true;
    }

    private boolean isNonBlockingApprovalRequired(IdentityRecord identityRecord) {
        return !identityRecord.isFirstUse() && !identityRecord.isNonBlockingApproval();
    }
}
