import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

admin.initializeApp();

/**
 * Cloud Function: Send Emergency SMS via Twilio
 * Triggered when emergency event is created in database
 */
export const sendEmergencySMS = functions.database
  .ref('/emergencies/{emergencyId}')
  .onCreate(async (snapshot, context) => {
    const emergency = snapshot.val();
    const userId = emergency.userId;
    
    try {
      // Get user data
      const userSnapshot = await admin.database()
        .ref(`/users/${userId}`)
        .once('value');
      const userData = userSnapshot.val();
      
      // Get emergency contacts
      const contactsSnapshot = await admin.database()
        .ref(`/emergencyContacts/${userId}`)
        .once('value');
      const contacts = contactsSnapshot.val();
      
      if (!contacts) {
        console.log('No emergency contacts found');
        return null;
      }
      
      // Build message
      const mapsLink = `https://maps.google.com/?q=${emergency.latitude},${emergency.longitude}`;
      const message = `🚨 EMERGENCY! ${userData.name} needs help!\n` +
                     `Mobile: ${userData.phone}\n` +
                     `Blood: ${userData.bloodGroup}\n` +
                     `Location: ${mapsLink}`;
      
      console.log('Emergency SMS would be sent to contacts:', Object.keys(contacts).length);
      console.log('Message:', message);
      
      // Log notification
      await admin.database()
        .ref(`/notifications/${userId}`)
        .push({
          message: `Emergency SMS prepared for ${Object.keys(contacts).length} contacts`,
          timestamp: admin.database.ServerValue.TIMESTAMP,
          type: 'sms_prepared',
          emergencyId: context.params.emergencyId
        });
      
      // TODO: Integrate with Twilio for actual SMS sending
      // Uncomment when Twilio is configured:
      /*
      const twilio = require('twilio');
      const twilioClient = twilio(
        functions.config().twilio.account_sid,
        functions.config().twilio.auth_token
      );
      
      const promises = Object.values(contacts).map((contact: any) => {
        return twilioClient.messages.create({
          body: message,
          from: functions.config().twilio.phone_number,
          to: contact.phoneNumber
        });
      });
      
      await Promise.all(promises);
      */
      
      return { success: true };
    } catch (error) {
      console.error('Error in sendEmergencySMS:', error);
      return { success: false, error };
    }
  });

/**
 * Cloud Function: Send Emergency Email
 */
export const sendEmergencyEmail = functions.database
  .ref('/emergencies/{emergencyId}')
  .onCreate(async (snapshot, context) => {
    const emergency = snapshot.val();
    const userId = emergency.userId;
    
    try {
      // Get user data
      const userSnapshot = await admin.database()
        .ref(`/users/${userId}`)
        .once('value');
      const userData = userSnapshot.val();
      
      // Get emergency contacts
      const contactsSnapshot = await admin.database()
        .ref(`/emergencyContacts/${userId}`)
        .once('value');
      const contacts = contactsSnapshot.val();
      
      if (!contacts) {
        console.log('No emergency contacts found');
        return null;
      }
      
      const mapsLink = `https://maps.google.com/?q=${emergency.latitude},${emergency.longitude}`;
      
      console.log('Emergency email would be sent');
      console.log('User:', userData.name);
      console.log('Location:', mapsLink);
      
      // TODO: Integrate with email service (nodemailer)
      // Uncomment when email is configured:
      /*
      const nodemailer = require('nodemailer');
      const emailTransporter = nodemailer.createTransport({
        service: 'gmail',
        auth: {
          user: functions.config().email.user,
          pass: functions.config().email.password
        }
      });
      
      const emailPromises = Object.values(contacts)
        .filter((contact: any) => contact.email)
        .map((contact: any) => {
          return emailTransporter.sendMail({
            from: functions.config().email.user,
            to: contact.email,
            subject: `🚨 EMERGENCY ALERT - ${userData.name}`,
            html: `
              <h2>Emergency Alert</h2>
              <p><strong>${userData.name}</strong> may need immediate assistance!</p>
              <ul>
                <li><strong>Mobile:</strong> ${userData.phone}</li>
                <li><strong>Blood Group:</strong> ${userData.bloodGroup}</li>
                <li><strong>Time:</strong> ${new Date(emergency.timestamp).toLocaleString()}</li>
                <li><strong>Location:</strong> <a href="${mapsLink}">View on Google Maps</a></li>
              </ul>
              <p>This is an automated emergency alert from the Accident Detection System.</p>
            `
          });
        });
      
      await Promise.all(emailPromises);
      */
      
      return { success: true };
    } catch (error) {
      console.error('Error in sendEmergencyEmail:', error);
      return { success: false, error };
    }
  });

/**
 * Cloud Function: Notify Emergency Services
 */
export const notifyEmergencyServices = functions.https.onCall(async (data, context) => {
  const { userId, emergencyId, latitude, longitude } = data;
  
  // Verify authentication
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }
  
  try {
    // Get user data
    const userSnapshot = await admin.database()
      .ref(`/users/${userId}`)
      .once('value');
    const userData = userSnapshot.val();
    
    if (!userData) {
      throw new functions.https.HttpsError('not-found', 'User data not found');
    }
    
    // Log emergency service notification
    await admin.database()
      .ref(`/emergencyServiceNotifications/${emergencyId}`)
      .set({
        userId,
        userName: userData.name,
        userPhone: userData.phone,
        bloodGroup: userData.bloodGroup,
        latitude,
        longitude,
        timestamp: admin.database.ServerValue.TIMESTAMP,
        status: 'notified'
      });
    
    console.log('Emergency services notified for user:', userData.name);
    console.log('Location:', latitude, longitude);
    
    // TODO: Integrate with actual emergency services API
    // This could be RapidSOS, local 911 API, or other emergency service providers
    
    return { 
      success: true, 
      message: 'Emergency services notified',
      emergencyId 
    };
  } catch (error) {
    console.error('Error in notifyEmergencyServices:', error);
    throw new functions.https.HttpsError('internal', 'Failed to notify emergency services');
  }
});

/**
 * Cloud Function: Send Push Notification
 */
export const sendPushNotification = functions.database
  .ref('/emergencies/{emergencyId}')
  .onCreate(async (snapshot, context) => {
    const emergency = snapshot.val();
    const userId = emergency.userId;
    
    try {
      // Get user's FCM tokens
      const tokensSnapshot = await admin.database()
        .ref(`/fcmTokens/${userId}`)
        .once('value');
      const tokens = tokensSnapshot.val();
      
      if (!tokens) {
        console.log('No FCM tokens found for user');
        return null;
      }
      
      const tokenList = Object.values(tokens) as string[];
      
      // Send notification
      const payload = {
        notification: {
          title: '🚨 Emergency Alert',
          body: 'An emergency has been detected. Emergency contacts have been notified.',
        },
        data: {
          emergencyId: context.params.emergencyId,
          latitude: emergency.latitude.toString(),
          longitude: emergency.longitude.toString(),
          type: emergency.type
        }
      };
      
      const response = await admin.messaging().sendToDevice(tokenList, payload);
      console.log('Push notifications sent:', response.successCount);
      
      return { success: true };
    } catch (error) {
      console.error('Error sending push notifications:', error);
      return { success: false, error };
    }
  });

/**
 * Cloud Function: Clean up old emergency records (runs daily)
 */
export const cleanupOldEmergencies = functions.pubsub
  .schedule('every 24 hours')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    const thirtyDaysAgo = Date.now() - (30 * 24 * 60 * 60 * 1000);
    
    try {
      const snapshot = await admin.database()
        .ref('/emergencies')
        .orderByChild('timestamp')
        .endAt(thirtyDaysAgo)
        .once('value');
      
      const updates: any = {};
      snapshot.forEach((child) => {
        updates[child.key!] = null;
      });
      
      if (Object.keys(updates).length > 0) {
        await admin.database().ref('/emergencies').update(updates);
        console.log(`Cleaned up ${Object.keys(updates).length} old emergency records`);
      } else {
        console.log('No old emergency records to clean up');
      }
      
      return null;
    } catch (error) {
      console.error('Error in cleanupOldEmergencies:', error);
      return null;
    }
  });

/**
 * Cloud Function: Log emergency event for analytics
 */
export const logEmergencyAnalytics = functions.database
  .ref('/emergencies/{emergencyId}')
  .onCreate(async (snapshot, context) => {
    const emergency = snapshot.val();
    
    try {
      // Log to analytics collection
      await admin.database()
        .ref('/analytics/emergencies')
        .push({
          emergencyId: context.params.emergencyId,
          userId: emergency.userId,
          type: emergency.type,
          timestamp: emergency.timestamp,
          latitude: emergency.latitude,
          longitude: emergency.longitude,
          speed: emergency.speed
        });
      
      console.log('Emergency analytics logged');
      return { success: true };
    } catch (error) {
      console.error('Error logging analytics:', error);
      return { success: false, error };
    }
  });
