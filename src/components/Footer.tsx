
import React from 'react';
import { Heart, Globe, Mail, Phone, MapPin } from 'lucide-react';

const Footer = () => {
  return (
    <footer className="bg-gradient-to-r from-gray-900 to-gray-800 text-white py-12">
      <div className="container mx-auto px-4">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-8">
          <div>
            <h3 className="text-2xl font-bold mb-4 flex items-center">
              <Heart className="h-6 w-6 text-orange-500 mr-2" />
              AI Voice Agents
            </h3>
            <p className="text-gray-300 mb-4">
              Transforming organizations worldwide with intelligent voice automation. 
              Made in India for the World.
            </p>
            <div className="flex items-center text-sm text-gray-400">
              <Globe className="h-4 w-4 mr-2" />
              Serving clients globally from India
            </div>
          </div>
          
          <div>
            <h4 className="text-lg font-semibold mb-4">Solutions</h4>
            <ul className="space-y-2 text-gray-300">
              <li>Customer Service Automation</li>
              <li>Sales & Lead Qualification</li>
              <li>Appointment Scheduling</li>
              <li>Internal Communications</li>
              <li>Data Collection & Analysis</li>
            </ul>
          </div>
          
          <div>
            <h4 className="text-lg font-semibold mb-4">Contact Info</h4>
            <div className="space-y-3 text-gray-300">
              <div className="flex items-center">
                <Mail className="h-4 w-4 mr-3 text-blue-400" />
                hello@aivoiceagents.com
              </div>
              <div className="flex items-center">
                <Phone className="h-4 w-4 mr-3 text-green-400" />
                +91 (800) 123-4567
              </div>
              <div className="flex items-center">
                <MapPin className="h-4 w-4 mr-3 text-red-400" />
                Bangalore, India
              </div>
            </div>
          </div>
        </div>
        
        <div className="border-t border-gray-700 pt-8 text-center text-gray-400">
          <p>&copy; 2024 AI Voice Agents. Made with ❤️ in India for the World.</p>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
