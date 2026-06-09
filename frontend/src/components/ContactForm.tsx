
import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Send, Building2, Users, Phone } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

const ContactForm = () => {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    company: '',
    phone: '',
    employees: '',
    useCase: '',
    message: ''
  });
  
  const { toast } = useToast();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    console.log('Form submitted:', formData);
    
    toast({
      title: "Inquiry Submitted Successfully!",
      description: "Our team will contact you within 24 hours to discuss your AI Voice Agent requirements.",
    });
    
    // Reset form
    setFormData({
      name: '',
      email: '',
      company: '',
      phone: '',
      employees: '',
      useCase: '',
      message: ''
    });
  };

  const handleChange = (field: string, value: string) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }));
  };

  return (
    <section 
      id="contact" 
      className="py-20 bg-gradient-to-br from-gray-50 to-blue-50"
      role="region"
      aria-labelledby="contact-heading"
    >
      <div className="container mx-auto px-4">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-12 animate-fade-in-up">
            <h2 id="contact-heading" className="text-4xl md:text-5xl font-bold text-gray-900 mb-6">
              Ready to Transform Your Business?
            </h2>
            <p className="text-xl text-gray-600">
              Get a personalized consultation and see how AI Voice Agents can optimize your operations.
            </p>
          </div>
          
          <Card className="shadow-2xl border-0 bg-white animate-scale-in animate-delay-200">
            <CardHeader className="bg-gradient-to-r from-blue-600 to-purple-600 text-white rounded-t-lg">
              <CardTitle className="text-2xl text-center flex items-center justify-center">
                <Building2 className="mr-3 h-6 w-6" aria-hidden="true" />
                Client Inquiry Form
              </CardTitle>
            </CardHeader>
            
            <CardContent className="p-8">
              <form onSubmit={handleSubmit} className="space-y-6" noValidate>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div>
                    <label htmlFor="name" className="block text-sm font-semibold text-gray-700 mb-2">
                      Full Name *
                    </label>
                    <Input
                      id="name"
                      required
                      value={formData.name}
                      onChange={(e) => handleChange('name', e.target.value)}
                      placeholder="Enter your full name"
                      className="h-12 focus-outline"
                      aria-describedby="name-error"
                    />
                  </div>
                  
                  <div>
                    <label htmlFor="email" className="block text-sm font-semibold text-gray-700 mb-2">
                      Business Email *
                    </label>
                    <Input
                      id="email"
                      type="email"
                      required
                      value={formData.email}
                      onChange={(e) => handleChange('email', e.target.value)}
                      placeholder="your.email@company.com"
                      className="h-12 focus-outline"
                      aria-describedby="email-error"
                    />
                  </div>
                </div>
                
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div>
                    <label htmlFor="company" className="block text-sm font-semibold text-gray-700 mb-2">
                      Company Name *
                    </label>
                    <Input
                      id="company"
                      required
                      value={formData.company}
                      onChange={(e) => handleChange('company', e.target.value)}
                      placeholder="Your company name"
                      className="h-12 focus-outline"
                    />
                  </div>
                  
                  <div>
                    <label htmlFor="phone" className="block text-sm font-semibold text-gray-700 mb-2">
                      Phone Number
                    </label>
                    <Input
                      id="phone"
                      type="tel"
                      value={formData.phone}
                      onChange={(e) => handleChange('phone', e.target.value)}
                      placeholder="+1 (555) 123-4567"
                      className="h-12 focus-outline"
                    />
                  </div>
                </div>
                
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div>
                    <label htmlFor="employees" className="block text-sm font-semibold text-gray-700 mb-2">
                      Company Size
                    </label>
                    <Select value={formData.employees} onValueChange={(value) => handleChange('employees', value)}>
                      <SelectTrigger id="employees" className="h-12 focus-outline" aria-label="Select company size">
                        <SelectValue placeholder="Select company size" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="1-10">1-10 employees</SelectItem>
                        <SelectItem value="11-50">11-50 employees</SelectItem>
                        <SelectItem value="51-200">51-200 employees</SelectItem>
                        <SelectItem value="201-1000">201-1000 employees</SelectItem>
                        <SelectItem value="1000+">1000+ employees</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  
                  <div>
                    <label htmlFor="useCase" className="block text-sm font-semibold text-gray-700 mb-2">
                      Primary Use Case
                    </label>
                    <Select value={formData.useCase} onValueChange={(value) => handleChange('useCase', value)}>
                      <SelectTrigger id="useCase" className="h-12 focus-outline" aria-label="Select primary use case">
                        <SelectValue placeholder="Select primary use case" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="customer-service">Customer Service</SelectItem>
                        <SelectItem value="sales">Sales & Lead Generation</SelectItem>
                        <SelectItem value="appointments">Appointment Scheduling</SelectItem>
                        <SelectItem value="surveys">Surveys & Data Collection</SelectItem>
                        <SelectItem value="internal">Internal Communications</SelectItem>
                        <SelectItem value="multiple">Multiple Use Cases</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                
                <div>
                  <label htmlFor="message" className="block text-sm font-semibold text-gray-700 mb-2">
                    Project Requirements & Goals
                  </label>
                  <Textarea
                    id="message"
                    value={formData.message}
                    onChange={(e) => handleChange('message', e.target.value)}
                    placeholder="Tell us about your specific requirements, current challenges, and what you hope to achieve with AI Voice Agents..."
                    className="min-h-[120px] resize-none focus-outline"
                    aria-describedby="message-help"
                  />
                  <div id="message-help" className="text-sm text-gray-500 mt-1">
                    Optional: Provide details about your needs to help us prepare for your consultation.
                  </div>
                </div>
                
                <div 
                  className="flex items-center space-x-4 p-4 bg-blue-50 rounded-lg"
                  role="note"
                  aria-label="Free consultation information"
                >
                  <div className="flex space-x-2" aria-hidden="true">
                    <Phone className="h-5 w-5 text-blue-600" />
                    <Users className="h-5 w-5 text-purple-600" />
                  </div>
                  <div className="text-sm text-gray-700">
                    <strong>Free Consultation:</strong> Our experts will analyze your needs and provide a customized solution roadmap within 24 hours.
                  </div>
                </div>
                
                <Button 
                  type="submit" 
                  className="w-full h-14 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white text-lg font-semibold rounded-lg transition-all duration-300 transform hover:scale-105 focus-outline"
                  aria-describedby="submit-help"
                >
                  Submit Inquiry & Get Free Consultation
                  <Send className="ml-2 h-5 w-5" aria-hidden="true" />
                </Button>
                <div id="submit-help" className="text-sm text-gray-500 text-center">
                  By submitting this form, you agree to be contacted by our team regarding your AI Voice Agent inquiry.
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>
  );
};

export default ContactForm;
